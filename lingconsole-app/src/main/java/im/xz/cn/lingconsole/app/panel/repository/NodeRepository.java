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

import im.xz.cn.lingconsole.app.panel.model.Node;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class NodeRepository {

    private final DatabaseManager db;

    public NodeRepository(DatabaseManager db) {
        this.db = db;
    }

    public DatabaseManager databaseManager() {
        return db;
    }

    public List<Node> findAll() {
        String sql = "SELECT id, name, url, key, status, style, created_at, updated_at FROM nodes ORDER BY created_at";
        List<Node> nodes = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                nodes.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询节点列表失败", e);
        }
        return nodes;
    }

    public Optional<Node> findById(String id) {
        String sql = "SELECT id, name, url, key, status, style, created_at, updated_at FROM nodes WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询节点失败", e);
        }
    }

    public Optional<Node> findByName(String name) {
        String sql = "SELECT id, name, url, key, status, style, created_at, updated_at FROM nodes WHERE name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询节点失败", e);
        }
    }

    public void insert(Node node) {
        String sql = "INSERT INTO nodes (id, name, url, key, status, style, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, node.getId());
            ps.setString(2, node.getName());
            ps.setString(3, node.getUrl());
            ps.setString(4, node.getKey());
            ps.setInt(5, node.getStatus());
            ps.setString(6, node.getStyle());
            ps.setLong(7, node.getCreatedAt());
            ps.setLong(8, node.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("插入节点失败", e);
        }
    }

    public void update(Node node) {
        String sql = "UPDATE nodes SET name = ?, url = ?, key = ?, status = ?, style = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, node.getName());
            ps.setString(2, node.getUrl());
            ps.setString(3, node.getKey());
            ps.setInt(4, node.getStatus());
            ps.setString(5, node.getStyle());
            ps.setLong(6, System.currentTimeMillis() / 1000);
            ps.setString(7, node.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新节点失败", e);
        }
    }

    public void updateStatus(String id, int status) {
        String sql = "UPDATE nodes SET status = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, status);
            ps.setLong(2, System.currentTimeMillis() / 1000);
            ps.setString(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新节点状态失败", e);
        }
    }

    public void updateStyle(String id, String style) {
        String sql = "UPDATE nodes SET style = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, style);
            ps.setLong(2, System.currentTimeMillis() / 1000);
            ps.setString(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新节点系统偏好失败", e);
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM nodes WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("删除节点失败", e);
        }
    }

    private Node mapRow(ResultSet rs) throws SQLException {
        Node node = new Node();
        node.setId(rs.getString("id"));
        node.setName(rs.getString("name"));
        node.setUrl(rs.getString("url"));
        node.setKey(rs.getString("key"));
        node.setStatus(rs.getInt("status"));
        node.setStyle(rs.getString("style") == null ? "auto" : rs.getString("style"));
        node.setCreatedAt(rs.getLong("created_at"));
        node.setUpdatedAt(rs.getLong("updated_at"));
        return node;
    }
}
