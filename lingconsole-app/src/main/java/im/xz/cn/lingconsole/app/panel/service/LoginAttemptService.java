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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class LoginAttemptService {

    private static final class Attempt {
        int failures;
        long lastFailure;
        long firstFailure;
    }


    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final int ipMaxAttempts;
    private final long lockoutMillis;
    private final int ratePerSecond;
    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "login-attempt-cleanup");
                t.setDaemon(true);
                return t;
            });

    public LoginAttemptService(int maxAttempts, int lockoutSeconds, int ratePerSecond) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.ipMaxAttempts = Math.max(1, maxAttempts) * 3;
        this.lockoutMillis = Math.max(1, lockoutSeconds) * 1000L;
        this.ratePerSecond = Math.max(1, ratePerSecond);
        cleanupScheduler.scheduleAtFixedRate(this::evictPeriodic, 5, 5, TimeUnit.MINUTES);
    }

    public void shutdown() {
        cleanupScheduler.shutdownNow();
    }


    private void evictPeriodic() {
        try {
            evictExpired();
            long cutoff = System.currentTimeMillis() / 1000 - IP_ENTRY_TTL_MILLIS / 1000;
            ipCounts.entrySet().removeIf(e -> e.getValue()[0] < cutoff);
            if (ipCounts.size() > MAX_IP_ENTRIES) {
                ipCounts.clear();
            }
            if (attempts.size() > MAX_ENTRIES) {
                evictOldest();
            }
        } catch (Exception e) {
            assert true;
        }
    }

    
    private String comboKey(String ip, String username) {
        return (ip == null ? "" : ip) + "|" + (username == null ? "" : username);
    }

    
    private String ipKey(String ip) {
        return (ip == null ? "" : ip);
    }

    
    public boolean isLocked(String ip, String username) {
        evictExpired();
        return isLockedKey(comboKey(ip, username), maxAttempts)
                || isLockedKey(ipKey(ip), ipMaxAttempts);
    }

    private boolean isLockedKey(String key, int threshold) {
        Attempt attempt = attempts.get(key);
        if (attempt == null || attempt.failures < threshold) {
            return false;
        }
        if (System.currentTimeMillis() - attempt.lastFailure >= lockoutMillis) {
            attempts.remove(key);
            return false;
        }
        return true;
    }

    
    public long remainingSeconds(String ip, String username) {
        Attempt attempt = attempts.get(comboKey(ip, username));
        if (attempt == null) {
            attempt = attempts.get(ipKey(ip));
        }
        if (attempt == null) {
            return 0;
        }
        long remaining = lockoutMillis - (System.currentTimeMillis() - attempt.lastFailure);
        return remaining > 0 ? remaining / 1000 : 0;
    }

    
    public void recordFailure(String ip, String username) {
        evictExpired();
        recordFailureKey(ipKey(ip));
        recordFailureKey(comboKey(ip, username));
    }

    private void recordFailureKey(String key) {
        long now = System.currentTimeMillis();
        Attempt attempt = attempts.computeIfAbsent(key, x -> {
            Attempt a = new Attempt();
            a.firstFailure = now;
            return a;
        });
        synchronized (attempt) {
            attempt.failures++;
            attempt.lastFailure = now;
        }
        if (attempts.size() > MAX_ENTRIES) {
            evictOldest();
        }
    }

    
    public void reset(String ip, String username) {
        attempts.remove(comboKey(ip, username));
        attempts.remove(ipKey(ip));
    }

    
    private void evictExpired() {
        if (attempts.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e ->
                now - e.getValue().lastFailure >= lockoutMillis
                        || now - e.getValue().firstFailure >= lockoutMillis);
    }

    private void evictOldest() {
        if (attempts.isEmpty()) {
            return;
        }
        String oldestKey = null;
        long oldestFirst = Long.MAX_VALUE;
        for (Map.Entry<String, Attempt> e : attempts.entrySet()) {
            if (e.getValue().firstFailure < oldestFirst) {
                oldestFirst = e.getValue().firstFailure;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            attempts.remove(oldestKey);
        }
    }

    
    
    

    
    private final Map<String, long[]> ipCounts = new ConcurrentHashMap<>();

    
    private static final int MAX_IP_ENTRIES = 50_000;

    
    private static final long IP_ENTRY_TTL_MILLIS = 60_000;

    
    public boolean isIpRateExceeded(String ip) {
        String k = ip == null ? "" : ip;
        long now = System.currentTimeMillis();
        long second = now / 1000;
        long[] entry = ipCounts.computeIfAbsent(k, x -> {
            evictStaleIpCounts(second);
            return new long[]{second, 0};
        });
        synchronized (entry) {
            if (entry[0] != second) {
                entry[0] = second;
                entry[1] = 0;
            }
            entry[1]++;
            return entry[1] > ratePerSecond;
        }
    }

    
    private void evictStaleIpCounts(long currentSecond) {
        if (ipCounts.size() <= MAX_IP_ENTRIES) {
            return;
        }
        long cutoff = currentSecond - IP_ENTRY_TTL_MILLIS / 1000;
        ipCounts.entrySet().removeIf(e -> e.getValue()[0] < cutoff);
        if (ipCounts.size() > MAX_IP_ENTRIES) {
            ipCounts.clear();
        }
    }

    
    public int size() {
        return attempts.size();
    }
}
