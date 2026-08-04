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

import im.xz.cn.lingconsole.app.panel.model.OperationLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class LogRepository {

    private final DatabaseManager db;

    public LogRepository(DatabaseManager db) {
        this.db = db;
    }

    public void insert(OperationLog log) {
        String sql = "INSERT INTO operation_logs (id, user_id, action, target, detail, ip, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, log.getId());
            ps.setString(2, log.getUserId());
            ps.setString(3, log.getAction());
            ps.setString(4, log.getTarget());
            ps.setString(5, log.getDetail());
            ps.setString(6, log.getIp());
            ps.setLong(7, log.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("写入操作日志失败", e);
        }
    }

    public List<OperationLog> findAll(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String sql = "SELECT id, user_id, action, target, detail, ip, created_at FROM operation_logs ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<OperationLog> logs = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, offset);
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

    private OperationLog mapRow(ResultSet rs) throws SQLException {
        OperationLog log = new OperationLog();
        log.setId(rs.getString("id"));
        log.setUserId(rs.getString("user_id"));
        log.setAction(rs.getString("action"));
        log.setTarget(rs.getString("target"));
        log.setDetail(rs.getString("detail"));
        log.setIp(rs.getString("ip"));
        log.setCreatedAt(rs.getLong("created_at"));
        return log;
    }
}
