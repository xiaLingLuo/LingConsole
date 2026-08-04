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

import im.xz.cn.lingconsole.app.panel.model.UserAppPermission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class UserAppPermissionRepository {

    private final DatabaseManager db;

    public UserAppPermissionRepository(DatabaseManager db) {
        this.db = db;
    }

    
    public Set<String> findAppIdsByUser(String userId) {
        String sql = "SELECT app_id FROM user_app_permissions WHERE user_id = ?";
        Set<String> ids = new HashSet<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString("app_id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询用户应用权限失败", e);
        }
        return ids;
    }

    
    public boolean hasAccess(String userId, String appId) {
        String sql = "SELECT COUNT(*) FROM user_app_permissions WHERE user_id = ? AND (app_id = ? OR app_id = ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, appId);
            ps.setString(3, UserAppPermission.ALL_APPS);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("检查用户应用权限失败", e);
        }
    }

    
    public void setAppIds(String userId, Set<String> appIds, String grantedBy) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM user_app_permissions WHERE user_id = ?")) {
                del.setString(1, userId);
                del.executeUpdate();
            }
            long now = System.currentTimeMillis() / 1000;
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT OR IGNORE INTO user_app_permissions (id, user_id, app_id, granted_by, created_at) VALUES (?, ?, ?, ?, ?)")) {
                for (String appId : appIds) {
                    ins.setString(1, im.xz.cn.lingconsole.common.util.IdUtil.uuid());
                    ins.setString(2, userId);
                    ins.setString(3, appId);
                    ins.setString(4, grantedBy);
                    ins.setLong(5, now);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("设置用户应用权限失败", e);
        }
    }

    public void deleteForUser(String userId) {
        String sql = "DELETE FROM user_app_permissions WHERE user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("删除用户应用权限失败", e);
        }
    }
}
