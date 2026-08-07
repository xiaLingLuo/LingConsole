package im.xz.cn.lingconsole.app.panel.service;

import im.xz.cn.lingconsole.app.panel.model.Session;
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.app.panel.repository.SessionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionServiceTest {

    @Test
    void bannedUserSessionIsRejectedAndDeleted() {
        User user = new User();
        user.setId("user-a");
        UserService users = new UserService(null, null) {
            @Override public User findById(String id) { return user; }
            @Override public boolean isBanned(User ignored) { return true; }
        };
        FakeSessionRepository sessions = new FakeSessionRepository(session("token-a", user.getId()));
        SessionService service = new SessionService(sessions, users);
        try {
            assertNull(service.validateToken("token-a"));
            assertTrue(sessions.deleted);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void permissionLookupFailureIsBannedFailClosed() {
        UserService users = new UserService(null, null);
        users.setPermissionService(new PermissionService(null, null) {
            @Override public Set<String> permissionsOf(String userId) {
                throw new RuntimeException("database unavailable");
            }
        });
        User user = new User();
        user.setId("user-a");
        assertTrue(users.isBanned(user));
    }

    private static Session session(String token, String userId) {
        Session session = new Session();
        session.setToken(token);
        session.setUserId(userId);
        session.setExpiresAt(System.currentTimeMillis() / 1000 + 60);
        return session;
    }

    private static final class FakeSessionRepository extends SessionRepository {
        private Session session;
        private boolean deleted;

        private FakeSessionRepository(Session session) {
            super(null);
            this.session = session;
        }

        @Override public Optional<Session> findByToken(String token) { return Optional.ofNullable(session); }
        @Override public void deleteByToken(String token) { deleted = true; session = null; }
        @Override public void cleanupExpired() {}
    }
}
