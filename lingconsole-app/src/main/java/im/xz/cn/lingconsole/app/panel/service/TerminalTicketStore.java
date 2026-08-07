/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package im.xz.cn.lingconsole.app.panel.service;

import im.xz.cn.lingconsole.common.util.IdUtil;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class TerminalTicketStore implements AutoCloseable {

    public record Ticket(String userId, String nodeId, String appId) {
    }

    public static final long TICKET_TTL_MILLIS = 60_000;

    private final long ttlMillis;
    private final int globalLimit;
    private final int perUserLimit;
    private final int perIpPerMinute;
    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();
    private final Map<String, ArrayDeque<Long>> ipAttempts = new ConcurrentHashMap<>();
    private final Object issueLock = new Object();
    private final ScheduledExecutorService cleanupScheduler;

    public TerminalTicketStore() {
        this(TICKET_TTL_MILLIS, 1000, 10, 10);
    }

    public TerminalTicketStore(long ttlMillis) {
        this(ttlMillis, 1000, 10, 10);
    }

    public TerminalTicketStore(long ttlMillis, int globalLimit, int perUserLimit, int perIpPerMinute) {
        this.ttlMillis = ttlMillis;
        this.globalLimit = globalLimit;
        this.perUserLimit = perUserLimit;
        this.perIpPerMinute = perIpPerMinute;
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "terminal-ticket-cleanup");
            t.setDaemon(true);
            return t;
        });
        long interval = Math.clamp(ttlMillis, 1000, 60_000);
        cleanupScheduler.scheduleAtFixedRate(this::cleanup, interval, interval, TimeUnit.MILLISECONDS);
    }

    public String issue(String userId, String nodeId, String appId, String ip) {
        if (userId == null || userId.isBlank()) {
            throw new LimitExceededException("用户无效");
        }
        synchronized (issueLock) {
            cleanup();
            if (!allowIp(ip)) {
                throw new LimitExceededException("终端票据申请过于频繁, 请稍后重试");
            }
            if (tickets.size() >= globalLimit) {
                throw new LimitExceededException("终端票据已达到全局上限");
            }
            long userTickets = tickets.values().stream()
                    .filter(e -> userId.equals(e.ticket().userId())).count();
            if (userTickets >= perUserLimit) {
                throw new LimitExceededException("该用户的待用终端票据已达到上限");
            }
            String ticket = IdUtil.uuidShort() + IdUtil.uuidShort();
            tickets.put(ticket, new Entry(new Ticket(userId, nodeId, appId), System.currentTimeMillis() + ttlMillis));
            return ticket;
        }
    }

    public String issue(String userId, String nodeId, String appId) {
        return issue(userId, nodeId, appId, "");
    }

    
    public Ticket consume(String ticket, String userId) {
        if (ticket == null || userId == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        final Ticket[] consumed = new Ticket[1];
        tickets.computeIfPresent(ticket, (key, entry) -> {
            if (entry.expiresAt() < now) {
                return null;
            }
            if (userId.equals(entry.ticket().userId())) {
                consumed[0] = entry.ticket();
                return null;
            }
            return entry;
        });
        return consumed[0];
    }

    public void cleanup() {
        synchronized (issueLock) {
            long now = System.currentTimeMillis();
            tickets.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
            long windowStart = now - 60_000;
            ipAttempts.entrySet().removeIf(e -> {
                ArrayDeque<Long> attempts = e.getValue();
                synchronized (attempts) {
                    while (!attempts.isEmpty() && attempts.peekFirst() < windowStart) attempts.pollFirst();
                    return attempts.isEmpty();
                }
            });
        }
    }

    int size() {
        return tickets.size();
    }

    private boolean allowIp(String ip) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;
        ArrayDeque<Long> attempts = ipAttempts.computeIfAbsent(ip == null ? "" : ip, key -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst() < windowStart) attempts.pollFirst();
            if (attempts.size() >= perIpPerMinute) return false;
            attempts.addLast(now);
            return true;
        }
    }

    @Override
    public void close() {
        cleanupScheduler.shutdownNow();
        tickets.clear();
        ipAttempts.clear();
    }

    private record Entry(Ticket ticket, long expiresAt) {}

    public static final class LimitExceededException extends RuntimeException {
        public LimitExceededException(String message) {
            super(message);
        }
    }
}
