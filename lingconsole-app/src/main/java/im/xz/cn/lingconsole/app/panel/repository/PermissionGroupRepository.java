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

import com.fasterxml.jackson.core.type.TypeReference;
import im.xz.cn.lingconsole.app.panel.model.PermissionGroup;
import im.xz.cn.lingconsole.common.model.ApiResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public class PermissionGroupRepository {

    private final DatabaseManager db;

    public PermissionGroupRepository(DatabaseManager db) {
        this.db = db;
    }

    public List<PermissionGroup> findAll() {
        String sql = "SELECT id, group_id, name, description, permissions, created_at FROM permission_groups ORDER BY created_at";
        List<PermissionGroup> groups = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                groups.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询权限组失败", e);
        }
        return groups;
    }

    public Optional<PermissionGroup> findById(String id) {
        String sql = "SELECT id, group_id, name, description, permissions, created_at FROM permission_groups WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询权限组失败", e);
        }
    }

    public void insert(PermissionGroup group) {
        String sql = "INSERT INTO permission_groups (id, group_id, name, description, permissions, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group.getId());
            ps.setString(2, group.getGroupId());
            ps.setString(3, group.getName());
            ps.setString(4, group.getDescription());
            ps.setString(5, toJson(group.getPermissions()));
            ps.setLong(6, group.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("插入权限组失败", e);
        }
    }

    public void update(PermissionGroup group) {
        String sql = "UPDATE permission_groups SET group_id = ?, name = ?, description = ?, permissions = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group.getGroupId());
            ps.setString(2, group.getName());
            ps.setString(3, group.getDescription());
            ps.setString(4, toJson(group.getPermissions()));
            ps.setString(5, group.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新权限组失败", e);
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM permission_groups WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("删除权限组失败", e);
        }
    }

    private PermissionGroup mapRow(ResultSet rs) throws SQLException {
        PermissionGroup group = new PermissionGroup();
        group.setId(rs.getString("id"));
        group.setGroupId(rs.getString("group_id"));
        group.setName(rs.getString("name"));
        group.setDescription(rs.getString("description"));
        group.setPermissions(fromJson(rs.getString("permissions")));
        group.setCreatedAt(rs.getLong("created_at"));
        return group;
    }

    private String toJson(Set<String> permissions) {
        try {
            return ApiResponse.mapper().writeValueAsString(new ArrayList<>(permissions));
        } catch (Exception e) {
            return "[]";
        }
    }

    private Set<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new HashSet<>();
        }
        try {
            return new HashSet<>(ApiResponse.mapper().readValue(json, new TypeReference<List<String>>() {
            }));
        } catch (Exception e) {
            return new HashSet<>();
        }
    }
}
