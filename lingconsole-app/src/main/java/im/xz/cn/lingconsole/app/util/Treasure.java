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
package im.xz.cn.lingconsole.app.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import im.xz.cn.lingconsole.app.panel.repository.DatabaseManager;
import im.xz.cn.lingconsole.common.config.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class Treasure {
    private static final Logger logger = LoggerFactory.getLogger(Treasure.class);

    private static final String TREASURE_URL = "https://treasure.xzrui.cn/treasure/lingconsole";
    private static final long INTERVAL_SECONDS = 60;
    private static final String SETTING_KEY = "server_uuid";
    private static final String CONFIG_DIR = "addons/Treasure";
    private static final String CONFIG_NAME = "config.yml";
    private static final String DEFAULT_CONFIG = """
            # Treasure是一个用于搜集一些信息以告诉程序作者有多少人在使用他的程序的功能。
            # 对作者而言，看到更多的人使用，能够激励他们创作。因此你应该使其保持true。
            # 这几乎不会消耗服务器资源~
            # 查看 https://treasure.xzrui.cn/ 了解更多哦~
            enabled: true
            """;

    private static Treasure instance;

    private final DatabaseManager db;
    private final String serverUUID;
    private ScheduledExecutorService scheduler;

    private Treasure(DatabaseManager db) {
        this.db = db;
        this.serverUUID = loadOrGenerateUUID();
    }

    public static synchronized Treasure init(DatabaseManager db, Path dataDir) {
        if (instance == null) {
            instance = new Treasure(db);
            if (instance.prepareConfig(dataDir)) {
                instance.start();
            } else {
                logger.info("[Treasure] 已禁用");
            }
        }
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.stop();
            instance = null;
        }
    }

    
    private boolean prepareConfig(Path dataDir) {
        try {
            Path dir = dataDir.resolve(CONFIG_DIR);
            Files.createDirectories(dir);
            Path configFile = dir.resolve(CONFIG_NAME);
            if (!Files.exists(configFile)) {
                Files.writeString(configFile, DEFAULT_CONFIG, StandardCharsets.UTF_8);
                logger.info("[Treasure] 已生成默认配置: {}", configFile);
            }
            return isEnabled(configFile);
        } catch (Exception e) {
            logger.warn("[Treasure] 初始化配置失败, 默认启用: {}", e.getMessage());
            return true;
        }
    }

    private static boolean isEnabled(Path configFile) {
        try {
            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1);
            }
            for (String line : content.split("\r?\n")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                if (t.startsWith("enabled")) {
                    String value = t.substring(t.indexOf(':') + 1).trim();
                    return !value.equalsIgnoreCase("false");
                }
            }
        } catch (Exception e) {
            logger.debug("[Treasure] 读取配置失败: {}", e.getMessage());
        }
        return true;
    }

    private void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "treasure-reporter");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                post();
            } catch (Exception e) {
                logger.debug("[Treasure] Report failed: {}", e.getMessage());
            }
        }, 0, INTERVAL_SECONDS, TimeUnit.SECONDS);
        logger.info("[Treasure] Reporter started (interval: {}s)", INTERVAL_SECONDS);
    }

    private void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private String loadOrGenerateUUID() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT setting_value FROM system_settings WHERE setting_key = ?")) {
            ps.setString(1, SETTING_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String raw = rs.getString(1);
                    if (raw != null && !raw.isEmpty() && !"null".equals(raw)) {
                        return raw;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("[Treasure] Failed to load UUID from DB: {}", e.getMessage());
        }

        String uuid = UUID.randomUUID().toString();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO system_settings (setting_key, setting_value) VALUES (?, ?)")) {
            ps.setString(1, SETTING_KEY);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.debug("[Treasure] Failed to save UUID: {}", e.getMessage());
        }
        return uuid;
    }

    private void post() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        
        root.put("uuid", serverUUID);
        root.put("ver", Constants.VERSION);

        byte[] body;
        try {
            body = mapper.writeValueAsBytes(root);
        } catch (Exception e) {
            return;
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(TREASURE_URL).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(body.length));
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int responseCode = conn.getResponseCode();
        } catch (Exception _) {

        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
