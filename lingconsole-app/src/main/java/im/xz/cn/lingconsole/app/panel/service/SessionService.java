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

import im.xz.cn.lingconsole.app.panel.model.Session;
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.app.panel.repository.SessionRepository;
import im.xz.cn.lingconsole.common.util.IdUtil;

import java.util.Optional;


public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserService userService;

    public SessionService(SessionRepository sessionRepository, UserService userService) {
        this.sessionRepository = sessionRepository;
        this.userService = userService;
    }

    
    public String createSession(String userId, int timeoutSeconds) {
        long now = System.currentTimeMillis() / 1000;
        String token = IdUtil.uuidShort();
        Session session = new Session();
        session.setId(IdUtil.uuid());
        session.setUserId(userId);
        session.setToken(token);
        session.setCreatedAt(now);
        session.setExpiresAt(now + timeoutSeconds);
        sessionRepository.insert(session);
        return token;
    }

    
    public User validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        sessionRepository.cleanupExpired();
        Optional<Session> session = sessionRepository.findByToken(token);
        if (session.isEmpty()) {
            return null;
        }
        if (session.get().getExpiresAt() < System.currentTimeMillis() / 1000) {
            sessionRepository.deleteByToken(token);
            return null;
        }
        return userService.findById(session.get().getUserId());
    }

    public void logout(String token) {
        if (token != null) {
            sessionRepository.deleteByToken(token);
        }
    }

    
    public void logoutAllForUser(String userId) {
        if (userId != null) {
            sessionRepository.deleteByUserId(userId);
        }
    }
}
