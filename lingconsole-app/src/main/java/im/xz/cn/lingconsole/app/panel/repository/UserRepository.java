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

import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.common.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class UserRepository {

    private final DatabaseManager db;

    public UserRepository(DatabaseManager db) {
        this.db = db;
    }

    public Optional<User> findById(String id) {
        String sql = "SELECT id, username, password, role, created_at, updated_at FROM users WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询用户失败", e);
        }
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, password, role, created_at, updated_at FROM users WHERE username = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询用户失败", e);
        }
    }

    public Optional<User> findByUsernameIgnoreCase(String username) {
        String sql = "SELECT id, username, password, role, created_at, updated_at FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询用户失败", e);
        }
    }

    public List<User> findAll() {
        String sql = "SELECT id, username, password, role, created_at, updated_at FROM users ORDER BY created_at";
        List<User> users = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询用户列表失败", e);
        }
        return users;
    }

    public void insert(User user) {
        String sql = "INSERT INTO users (id, username, password, role, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPassword());
            ps.setInt(4, user.getRole().value());
            ps.setLong(5, user.getCreatedAt());
            ps.setLong(6, user.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("插入用户失败", e);
        }
    }

    public void updatePassword(String id, String newPasswordHash) {
        String sql = "UPDATE users SET password = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setLong(2, System.currentTimeMillis() / 1000);
            ps.setString(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新密码失败", e);
        }
    }

    public void update(User user) {
        String sql = "UPDATE users SET username = ?, password = ?, role = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setInt(3, user.getRole().value());
            ps.setLong(4, user.getUpdatedAt());
            ps.setString(5, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新用户失败", e);
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("删除用户失败", e);
        }
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("统计用户失败", e);
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getString("id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setRole(UserRole.fromValue(rs.getInt("role")));
        user.setCreatedAt(rs.getLong("created_at"));
        user.setUpdatedAt(rs.getLong("updated_at"));
        return user;
    }
}
