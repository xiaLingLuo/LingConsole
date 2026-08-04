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
package im.xz.cn.lingconsole.daemon.service;

import im.xz.cn.lingconsole.common.util.IdUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class PassportManager {

    public static final long DEFAULT_TTL_MILLIS = 60_000; 

    private static final class Passport {
        final String token;
        final String terminalId;
        final long expiresAt;

        Passport(String terminalId) {
            this.token = IdUtil.uuidShort() + IdUtil.uuidShort();
            this.terminalId = terminalId;
            this.expiresAt = System.currentTimeMillis() + DEFAULT_TTL_MILLIS;
        }
    }

    private final Map<String, Passport> passports = new ConcurrentHashMap<>();

    
    public String register(String terminalId) {
        Passport passport = new Passport(terminalId);
        passports.put(passport.token, passport);
        cleanup();
        return passport.token;
    }

    
    public String consume(String token) {
        if (token == null) {
            return null;
        }
        Passport passport = passports.remove(token);
        if (passport == null) {
            return null;
        }
        if (passport.expiresAt < System.currentTimeMillis()) {
            return null;
        }
        return passport.terminalId;
    }

    
    public java.util.List<String> cleanup() {
        long now = System.currentTimeMillis();
        java.util.List<String> dropped = new java.util.ArrayList<>();
        passports.entrySet().removeIf(e -> {
            if (e.getValue().expiresAt < now) {
                dropped.add(e.getValue().terminalId);
                return true;
            }
            return false;
        });
        return dropped;
    }
}
