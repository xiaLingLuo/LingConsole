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

import im.xz.cn.lingconsole.app.panel.model.PermissionGroup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class UserGroupRepository {

    private final DatabaseManager db;
    private final PermissionGroupRepository groupRepository;

    public UserGroupRepository(DatabaseManager db, PermissionGroupRepository groupRepository) {
        this.db = db;
        this.groupRepository = groupRepository;
    }

    
    public List<PermissionGroup> findGroupsByUser(String userId) {
        String sql = "SELECT group_id FROM user_groups WHERE user_id = ?";
        List<PermissionGroup> groups = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    groupRepository.findById(rs.getString("group_id")).ifPresent(groups::add);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询用户权限组失败", e);
        }
        return groups;
    }

    public void setGroups(String userId, List<String> groupIds) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM user_groups WHERE user_id = ?")) {
                del.setString(1, userId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT OR IGNORE INTO user_groups (user_id, group_id) VALUES (?, ?)")) {
                for (String groupId : groupIds) {
                    ins.setString(1, userId);
                    ins.setString(2, groupId);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("设置用户权限组失败", e);
        }
    }
}
