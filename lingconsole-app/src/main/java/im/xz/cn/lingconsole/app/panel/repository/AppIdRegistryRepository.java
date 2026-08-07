/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package im.xz.cn.lingconsole.app.panel.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

public class AppIdRegistryRepository {

    private final DatabaseManager db;

    public AppIdRegistryRepository(DatabaseManager db) {
        this.db = db;
    }

    public boolean reserve(String appId, String nodeId) {
        String sql = "INSERT INTO app_id_registry (app_id, node_id, state, updated_at) VALUES (?, ?, 'reserved', ?)";
        try (Connection conn = connection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId);
            ps.setString(2, nodeId);
            ps.setLong(3, now());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 19 || (e.getMessage() != null && e.getMessage().contains("UNIQUE"))) {
                return false;
            }
            throw failure("预留应用 ID 失败", e);
        }
    }

    public boolean activate(String appId, String nodeId) {
        return updateState(appId, nodeId, "reserved", "active") == 1 || owns(appId, nodeId);
    }

    public void releaseReservation(String appId, String nodeId) {
        delete(appId, nodeId, "reserved");
    }

    public void releaseOwned(String appId, String nodeId) {
        delete(appId, nodeId, "active");
    }

    public boolean owns(String appId, String nodeId) {
        String sql = "SELECT 1 FROM app_id_registry WHERE app_id = ? AND node_id = ? AND state = 'active'";
        try (Connection conn = connection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId);
            ps.setString(2, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw failure("校验应用归属失败", e);
        }
    }

    public void synchronize(String nodeId, Set<String> appIds) {
        try (Connection conn = connection()) {
            conn.setAutoCommit(false);
            try {
                for (String appId : appIds) {
                    synchronizeOne(conn, appId, nodeId);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw failure("同步应用 ID 注册表失败", e);
        }
    }

    private void synchronizeOne(Connection conn, String appId, String nodeId) throws SQLException {
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT OR IGNORE INTO app_id_registry (app_id, node_id, state, updated_at) VALUES (?, ?, 'active', ?)")) {
            insert.setString(1, appId);
            insert.setString(2, nodeId);
            insert.setLong(3, now());
            insert.executeUpdate();
        }
        try (PreparedStatement select = conn.prepareStatement(
                "SELECT node_id, state FROM app_id_registry WHERE app_id = ?")) {
            select.setString(1, appId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("应用 ID 注册记录丢失: " + appId);
                }
                String owner = rs.getString("node_id");
                String state = rs.getString("state");
                if (!nodeId.equals(owner)) {
                    try (PreparedStatement conflict = conn.prepareStatement(
                            "UPDATE app_id_registry SET state = 'conflict', updated_at = ? WHERE app_id = ?")) {
                        conflict.setLong(1, now());
                        conflict.setString(2, appId);
                        conflict.executeUpdate();
                    }
                } else if ("reserved".equals(state)) {
                    try (PreparedStatement activate = conn.prepareStatement(
                            "UPDATE app_id_registry SET state = 'active', updated_at = ? WHERE app_id = ? AND node_id = ? AND state = 'reserved'")) {
                        activate.setLong(1, now());
                        activate.setString(2, appId);
                        activate.setString(3, nodeId);
                        activate.executeUpdate();
                    }
                }
            }
        }
    }

    private int updateState(String appId, String nodeId, String from, String to) {
        String sql = "UPDATE app_id_registry SET state = ?, updated_at = ? WHERE app_id = ? AND node_id = ? AND state = ?";
        try (Connection conn = connection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, to);
            ps.setLong(2, now());
            ps.setString(3, appId);
            ps.setString(4, nodeId);
            ps.setString(5, from);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw failure("更新应用 ID 状态失败", e);
        }
    }

    private void delete(String appId, String nodeId, String state) {
        try (Connection conn = connection()) {
            delete(conn, appId, nodeId, state);
        } catch (SQLException e) {
            throw failure("释放应用 ID 失败", e);
        }
    }

    private void delete(Connection conn, String appId, String nodeId, String state) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM app_id_registry WHERE app_id = ? AND node_id = ? AND state = ?")) {
            ps.setString(1, appId);
            ps.setString(2, nodeId);
            ps.setString(3, state);
            ps.executeUpdate();
        }
    }

    private Connection connection() throws SQLException {
        Connection conn = db.getConnection();
        try (var statement = conn.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return conn;
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    private static RuntimeException failure(String message, SQLException cause) {
        return new RuntimeException(message, cause);
    }
}
