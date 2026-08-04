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

import im.xz.cn.lingconsole.app.panel.model.RootAccount;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;


public class RootAccountRepository {

    private final DatabaseManager db;

    public RootAccountRepository(DatabaseManager db) {
        this.db = db;
    }

    public Optional<RootAccount> findRoot() {
        String sql = "SELECT id, username, password, created_at, updated_at FROM root_account WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, RootAccount.ROOT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询 root 账户失败", e);
        }
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM root_account";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("统计 root 账户失败", e);
        }
    }

    public void insert(RootAccount account) {
        String sql = "INSERT INTO root_account (id, username, password, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, account.getId());
            ps.setString(2, account.getUsername());
            ps.setString(3, account.getPassword());
            ps.setLong(4, account.getCreatedAt());
            ps.setLong(5, account.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("插入 root 账户失败", e);
        }
    }

    public void updatePassword(String passwordHash) {
        String sql = "UPDATE root_account SET password = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setLong(2, System.currentTimeMillis() / 1000);
            ps.setString(3, RootAccount.ROOT_ID);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新 root 密码失败", e);
        }
    }

    private RootAccount mapRow(ResultSet rs) throws SQLException {
        RootAccount account = new RootAccount();
        account.setId(rs.getString("id"));
        account.setUsername(rs.getString("username"));
        account.setPassword(rs.getString("password"));
        account.setCreatedAt(rs.getLong("created_at"));
        account.setUpdatedAt(rs.getLong("updated_at"));
        return account;
    }
}
