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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;


public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private final HikariDataSource dataSource;
    private final String dbPath;

    public DatabaseManager(String dbPath) throws IOException {
        this.dbPath = dbPath;
        Path parent = Paths.get(dbPath).getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);
        config.setPoolName("lingconsole-sqlite");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        
        config.addDataSourceProperty("foreign_keys", "false");

        this.dataSource = new HikariDataSource(config);
        initSchema();
        log.info("数据库已初始化: {}", dbPath);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public String dbPath() {
        return dbPath;
    }

    private void initSchema() throws IOException {
        String schema = """
                CREATE TABLE IF NOT EXISTS users (
                    id          TEXT PRIMARY KEY,
                    username    TEXT NOT NULL UNIQUE,
                    password    TEXT NOT NULL,
                    role        INTEGER NOT NULL DEFAULT 0,
                    created_at  INTEGER NOT NULL,
                    updated_at  INTEGER NOT NULL
                );

                CREATE TABLE IF NOT EXISTS nodes (
                    id          TEXT PRIMARY KEY,
                    name        TEXT NOT NULL,
                    url         TEXT NOT NULL,
                    key         TEXT NOT NULL,
                    status      INTEGER NOT NULL DEFAULT 0,
                    style       TEXT NOT NULL DEFAULT 'auto',
                    created_at  INTEGER NOT NULL,
                    updated_at  INTEGER NOT NULL
                );

                CREATE TABLE IF NOT EXISTS sessions (
                    id          TEXT PRIMARY KEY,
                    user_id     TEXT NOT NULL,
                    token       TEXT NOT NULL UNIQUE,
                    expires_at  INTEGER NOT NULL,
                    created_at  INTEGER NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id)
                );

                CREATE TABLE IF NOT EXISTS operation_logs (
                    id          TEXT PRIMARY KEY,
                    source_type TEXT NOT NULL DEFAULT 'CORE',
                    plugin_name TEXT,
                    actor_type  TEXT NOT NULL DEFAULT 'USER',
                    user_id     TEXT,
                    node_id     TEXT,
                    app_id      TEXT,
                    request_id  TEXT,
                    action      TEXT NOT NULL,
                    target      TEXT,
                    detail      TEXT,
                    ip          TEXT,
                    created_at  INTEGER NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id)
                );

                CREATE TABLE IF NOT EXISTS root_account (
                    id          TEXT PRIMARY KEY,
                    username    TEXT NOT NULL UNIQUE,
                    password    TEXT NOT NULL,
                    created_at  INTEGER NOT NULL,
                    updated_at  INTEGER NOT NULL
                );

                CREATE TABLE IF NOT EXISTS permission_groups (
                    id          TEXT PRIMARY KEY,
                    group_id    TEXT,
                    name        TEXT NOT NULL UNIQUE,
                    description TEXT,
                    permissions TEXT NOT NULL,
                    created_at  INTEGER NOT NULL
                );

                CREATE TABLE IF NOT EXISTS user_groups (
                    user_id  TEXT NOT NULL,
                    group_id TEXT NOT NULL,
                    PRIMARY KEY (user_id, group_id),
                    FOREIGN KEY (user_id) REFERENCES users(id),
                    FOREIGN KEY (group_id) REFERENCES permission_groups(id)
                );

                CREATE TABLE IF NOT EXISTS system_settings (
                    setting_key   TEXT PRIMARY KEY,
                    setting_value TEXT
                );

                CREATE TABLE IF NOT EXISTS addon_data (
                    addon TEXT NOT NULL,
                    k     TEXT NOT NULL,
                    v     TEXT,
                    PRIMARY KEY (addon, k)
                );

                CREATE TABLE IF NOT EXISTS app_id_registry (
                    app_id      TEXT PRIMARY KEY,
                    node_id     TEXT NOT NULL,
                    state       TEXT NOT NULL,
                    updated_at  INTEGER NOT NULL
                );

                CREATE INDEX IF NOT EXISTS idx_nodes_name ON nodes(name);
                CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(token);
                CREATE INDEX IF NOT EXISTS idx_logs_created_at ON operation_logs(created_at);
                CREATE INDEX IF NOT EXISTS idx_logs_source_created ON operation_logs(source_type, created_at);
                CREATE INDEX IF NOT EXISTS idx_logs_plugin_name ON operation_logs(plugin_name);
                CREATE INDEX IF NOT EXISTS idx_logs_user_id ON operation_logs(user_id);
                CREATE INDEX IF NOT EXISTS idx_logs_node_id ON operation_logs(node_id);
                CREATE INDEX IF NOT EXISTS idx_logs_app_id ON operation_logs(app_id);
                CREATE INDEX IF NOT EXISTS idx_logs_request_id ON operation_logs(request_id);
                CREATE INDEX IF NOT EXISTS idx_user_groups_user ON user_groups(user_id);
                CREATE INDEX IF NOT EXISTS idx_app_id_registry_node ON app_id_registry(node_id);
                """;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : schema.split(";")) {
                if (!sql.isBlank()) {
                    stmt.execute(sql);
                }
            }
        } catch (SQLException e) {
            throw new IOException("初始化数据库表失败", e);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
