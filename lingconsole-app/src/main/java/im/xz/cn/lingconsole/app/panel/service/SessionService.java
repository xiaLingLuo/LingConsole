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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class SessionService {

    public interface RevocationListener {
        void tokenRevoked(String token);
        void userRevoked(String userId);
    }

    private final SessionRepository sessionRepository;
    private final UserService userService;
    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "session-cleanup");
                t.setDaemon(true);
                return t;
            });
    private volatile RevocationListener revocationListener;

    public SessionService(SessionRepository sessionRepository, UserService userService) {
        this.sessionRepository = sessionRepository;
        this.userService = userService;
        cleanupScheduler.scheduleAtFixedRate(sessionRepository::cleanupExpired, 5, 5, TimeUnit.MINUTES);
    }

    public void shutdown() {
        cleanupScheduler.shutdownNow();
    }

    public void setRevocationListener(RevocationListener revocationListener) {
        this.revocationListener = revocationListener;
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
        Optional<Session> session = sessionRepository.findByToken(token);
        if (session.isEmpty()) {
            return null;
        }
        if (session.get().getExpiresAt() < System.currentTimeMillis() / 1000) {
            sessionRepository.deleteByToken(token);
            return null;
        }
        User user = userService.findById(session.get().getUserId());
        if (user == null || userService.isBanned(user)) {
            sessionRepository.deleteByToken(token);
            return null;
        }
        return user;
    }

    public void logout(String token) {
        if (token != null) {
            sessionRepository.deleteByToken(token);
            RevocationListener listener = revocationListener;
            if (listener != null) listener.tokenRevoked(token);
        }
    }

    
    public void logoutAllForUser(String userId) {
        if (userId != null) {
            sessionRepository.deleteByUserId(userId);
            RevocationListener listener = revocationListener;
            if (listener != null) listener.userRevoked(userId);
        }
    }
}
