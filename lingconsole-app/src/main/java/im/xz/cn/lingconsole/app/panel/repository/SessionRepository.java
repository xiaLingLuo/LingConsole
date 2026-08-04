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
package im.xz.cn.lingconsole.app.panel.repository;

import im.xz.cn.lingconsole.app.panel.model.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;


public class SessionRepository {

    private final DatabaseManager db;

    public SessionRepository(DatabaseManager db) {
        this.db = db;
    }

    public void insert(Session session) {
        String sql = "INSERT INTO sessions (id, user_id, token, expires_at, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, session.getId());
            ps.setString(2, session.getUserId());
            ps.setString(3, session.getToken());
            ps.setLong(4, session.getExpiresAt());
            ps.setLong(5, session.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("插入会话失败", e);
        }
    }

    public Optional<Session> findByToken(String token) {
        String sql = "SELECT id, user_id, token, expires_at, created_at FROM sessions WHERE token = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询会话失败", e);
        }
    }

    public void deleteByToken(String token) {
        String sql = "DELETE FROM sessions WHERE token = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("删除会话失败", e);
        }
    }

    public void deleteByUserId(String userId) {
        String sql = "DELETE FROM sessions WHERE user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("删除用户会话失败", e);
        }
    }

    public void cleanupExpired() {
        String sql = "DELETE FROM sessions WHERE expires_at < ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis() / 1000);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("清理过期会话失败", e);
        }
    }

    private Session mapRow(ResultSet rs) throws SQLException {
        Session session = new Session();
        session.setId(rs.getString("id"));
        session.setUserId(rs.getString("user_id"));
        session.setToken(rs.getString("token"));
        session.setExpiresAt(rs.getLong("expires_at"));
        session.setCreatedAt(rs.getLong("created_at"));
        return session;
    }
}
