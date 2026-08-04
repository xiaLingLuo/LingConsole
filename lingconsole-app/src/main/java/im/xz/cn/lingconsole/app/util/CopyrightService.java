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

import im.xz.cn.lingconsole.app.panel.repository.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CopyrightService {

    private static final Logger log = LoggerFactory.getLogger(CopyrightService.class);
    private static final String API_URL = "https://api.im.xz.cn/time?format=yyyy";
    private static final String SETTING_KEY = "copyright_year";
    private static final long REFRESH_HOURS = 24;
    private static final String DEFAULT_YEAR = "2026";
    private static volatile String year = DEFAULT_YEAR;
    private static volatile CopyrightService instance;
    private final DatabaseManager db;
    private ScheduledExecutorService scheduler;

    public static synchronized CopyrightService init(DatabaseManager db) {
        if (instance == null) {
            instance = new CopyrightService(db);
            instance.start();
        }
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.stop();
            instance = null;
        }
    }

    public static String year() {
        return year;
    }

    private CopyrightService(DatabaseManager db) {
        this.db = db;
        String stored = loadStoredYear();
        if (stored != null) {
            year = stored;
        }
    }

    private void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "copyright-year");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::refresh, 0, REFRESH_HOURS, TimeUnit.HOURS);
    }

    private void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void refresh() {
        try {
            fetchAndSave();
        } catch (Exception e) {
            //log.debug("版权年份获取失败: {}", e.getMessage());
        }
    }

    private void fetchAndSave() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body() == null ? "" : resp.body().trim();
                if (body.matches("\\d{4}")) {
                    year = body;
                    saveYear(body);
                }
            }
        }
    }

    private String loadStoredYear() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT setting_value FROM system_settings WHERE setting_key = ?")) {
            ps.setString(1, SETTING_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null && v.matches("\\d{4}")) {
                        return v;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("{}", e.getMessage());
        }
        return null;
    }

    private void saveYear(String value) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO system_settings (setting_key, setting_value) VALUES (?, ?)")) {
            ps.setString(1, SETTING_KEY);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (Exception e) {
            //log.debug("保存版权年份失败: {}", e.getMessage());
        }
    }
}
