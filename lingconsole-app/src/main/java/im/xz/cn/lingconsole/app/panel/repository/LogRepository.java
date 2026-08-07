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

import im.xz.cn.lingconsole.app.panel.model.OperationLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LogRepository {

    private static final String SELECT_COLUMNS = """
            SELECT l.id, l.source_type, l.plugin_name, l.actor_type, l.user_id,
                   u.username, l.node_id, n.name AS node_name, l.app_id, l.request_id,
                   l.action, l.target, l.detail, l.ip, l.created_at
            FROM operation_logs l
            LEFT JOIN users u ON u.id = l.user_id
            LEFT JOIN nodes n ON n.id = l.node_id
            """;

    public record Query(String q, String sourceType, String pluginName, String userId,
                        String nodeId, String appId, String action, String requestId,
                        Long startTime, Long endTime) {
    }

    private final DatabaseManager db;

    public LogRepository(DatabaseManager db) {
        this.db = db;
    }

    public void insert(OperationLog log) {
        String sql = """
                INSERT INTO operation_logs
                    (id, source_type, plugin_name, actor_type, user_id, node_id, app_id,
                     request_id, action, target, detail, ip, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, log.getId());
            ps.setString(2, log.getSourceType());
            ps.setString(3, log.getPluginName());
            ps.setString(4, log.getActorType());
            ps.setString(5, log.getUserId());
            ps.setString(6, log.getNodeId());
            ps.setString(7, log.getAppId());
            ps.setString(8, log.getRequestId());
            ps.setString(9, log.getAction());
            ps.setString(10, log.getTarget());
            ps.setString(11, log.getDetail());
            ps.setString(12, log.getIp());
            ps.setLong(13, log.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("写入操作日志失败", e);
        }
    }

    public void updateDetail(String id, String detail) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE operation_logs SET detail = ? WHERE id = ?")) {
            ps.setString(1, detail);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新操作日志失败", e);
        }
    }

    public List<OperationLog> find(Query query, int page, int pageSize) {
        SqlFilter filter = filter(query);
        String sql = SELECT_COLUMNS + filter.sql() + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?";
        List<OperationLog> logs = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = bind(ps, filter.parameters());
            ps.setInt(index++, pageSize);
            ps.setLong(index, (long) (page - 1) * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询操作日志失败", e);
        }
        return logs;
    }

    public long count(Query query) {
        SqlFilter filter = filter(query);
        String sql = "SELECT COUNT(*) FROM operation_logs l "
                + "LEFT JOIN users u ON u.id = l.user_id "
                + "LEFT JOIN nodes n ON n.id = l.node_id " + filter.sql();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, filter.parameters());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("统计操作日志失败", e);
        }
    }

    public List<OperationLog> findAll(int page, int pageSize) {
        return find(new Query(null, "CORE", null, null, null, null, null, null, null, null), page, pageSize);
    }

    public List<OperationLog> findByUserId(String userId, int limit) {
        return find(new Query(null, "CORE", null, userId, null, null, null, null, null, null), 1, limit);
    }

    private SqlFilter filter(Query query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (!"ALL".equals(query.sourceType())) {
            where.append(" AND l.source_type = ?");
            parameters.add(query.sourceType());
        }
        addEquals(where, parameters, "l.plugin_name", query.pluginName());
        addEquals(where, parameters, "l.user_id", query.userId());
        addEquals(where, parameters, "l.node_id", query.nodeId());
        addEquals(where, parameters, "l.app_id", query.appId());
        addEquals(where, parameters, "l.action", query.action());
        addEquals(where, parameters, "l.request_id", query.requestId());
        if (query.startTime() != null) {
            where.append(" AND l.created_at >= ?");
            parameters.add(query.startTime());
        }
        if (query.endTime() != null) {
            where.append(" AND l.created_at <= ?");
            parameters.add(query.endTime());
        }
        if (query.q() != null && !query.q().isBlank()) {
            String like = "%" + escapeLike(query.q()) + "%";
            where.append(" AND (l.action LIKE ? ESCAPE '\\' OR l.target LIKE ? ESCAPE '\\'")
                    .append(" OR l.detail LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\'")
                    .append(" OR l.plugin_name LIKE ? ESCAPE '\\' OR n.name LIKE ? ESCAPE '\\'")
                    .append(" OR l.app_id LIKE ? ESCAPE '\\')");
            for (int i = 0; i < 7; i++) {
                parameters.add(like);
            }
        }
        return new SqlFilter(where.toString(), parameters);
    }

    private static void addEquals(StringBuilder where, List<Object> parameters, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = ?");
            parameters.add(value);
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static int bind(PreparedStatement ps, List<Object> parameters) throws SQLException {
        int index = 1;
        for (Object parameter : parameters) {
            ps.setObject(index++, parameter);
        }
        return index;
    }

    private OperationLog mapRow(ResultSet rs) throws SQLException {
        OperationLog log = new OperationLog();
        log.setId(rs.getString("id"));
        log.setSourceType(rs.getString("source_type"));
        log.setPluginName(rs.getString("plugin_name"));
        log.setActorType(rs.getString("actor_type"));
        log.setUserId(rs.getString("user_id"));
        log.setUsername(rs.getString("username"));
        log.setNodeId(rs.getString("node_id"));
        log.setNodeName(rs.getString("node_name"));
        log.setAppId(rs.getString("app_id"));
        log.setRequestId(rs.getString("request_id"));
        log.setAction(rs.getString("action"));
        log.setTarget(rs.getString("target"));
        log.setDetail(rs.getString("detail"));
        log.setIp(rs.getString("ip"));
        log.setCreatedAt(rs.getLong("created_at"));
        return log;
    }

    private record SqlFilter(String sql, List<Object> parameters) {
    }
}
