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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class TerminalTicketStore {

    public record Ticket(String userId, String nodeId, String appId) {
    }

    public static final long TICKET_TTL_MILLIS = 60_000;

    private final long ttlMillis;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<String, Long> expiresAt = new ConcurrentHashMap<>();

    public TerminalTicketStore() {
        this(TICKET_TTL_MILLIS);
    }

    public TerminalTicketStore(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public String issue(String userId, String nodeId, String appId) {
        String ticket = IdUtil.uuidShort() + IdUtil.uuidShort();
        tickets.put(ticket, new Ticket(userId, nodeId, appId));
        expiresAt.put(ticket, System.currentTimeMillis() + ttlMillis);
        return ticket;
    }

    
    public Ticket consume(String ticket, String userId) {
        if (ticket == null || userId == null) {
            return null;
        }
        Ticket t = tickets.get(ticket);
        if (t == null) {
            return null;
        }
        Long exp = expiresAt.get(ticket);
        if (exp == null || exp < System.currentTimeMillis()) {
            tickets.remove(ticket);
            expiresAt.remove(ticket);
            return null;
        }
        if (!userId.equals(t.userId())) {
            return null;
        }
        tickets.remove(ticket);
        expiresAt.remove(ticket);
        return t;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        expiresAt.entrySet().removeIf(e -> e.getValue() < now);
        tickets.keySet().removeIf(t -> !expiresAt.containsKey(t));
    }
}
