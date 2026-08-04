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
package im.xz.cn.lingconsole.app.panel;

import im.xz.cn.lingconsole.common.config.Constants;
import im.xz.cn.lingconsole.common.config.TomlConfig;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


public class PanelConfig extends TomlConfig {

    private final String host;
    private final int port;
    private final int sessionTimeout;
    private final int maxLoginAttempts;
    private final int lockoutDuration;
    private final int rateLimitPerSecond;
    private final String theme;
    private final String language;
    private final String dbPath;

    private PanelConfig(TomlParseResult r) {
        super(r);
        host = str("server.host", "0.0.0.0");
        port = intVal("server.port", Constants.DEFAULT_WEB_PORT);
        sessionTimeout = intVal("auth.sessionTimeout", 3600);
        maxLoginAttempts = intVal("auth.maxLoginAttempts", 5);
        lockoutDuration = intVal("auth.lockoutDuration", 900);
        rateLimitPerSecond = intVal("security.rateLimitPerSecond", 8);
        theme = str("web.theme", "default");
        language = str("web.language", "zh_CN");
        dbPath = str("database.path", Constants.WEB_DIR + "/data/lingconsole.db");
    }

    public static PanelConfig load(Path path) throws IOException {
        if (Files.exists(path)) {
            return new PanelConfig(parse(path));
        }
        return new PanelConfig(Toml.parse(""));
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public int sessionTimeout() {
        return sessionTimeout;
    }

    public int maxLoginAttempts() {
        return maxLoginAttempts;
    }

    public int lockoutDuration() {
        return lockoutDuration;
    }

    public int rateLimitPerSecond() {
        return rateLimitPerSecond;
    }

    public String theme() {
        return theme;
    }

    public String language() {
        return language;
    }

    public String dbPath() {
        return dbPath;
    }

    public static String defaultToml() {
        return """
                [server]
                host = "0.0.0.0"
                port = 55600

                [auth]
                sessionTimeout = 3600
                maxLoginAttempts = 5
                lockoutDuration = 900

                [security]
                rateLimitPerSecond = 8

                [web]
                theme = "default"
                language = "zh_CN"

                [database]
                path = "/lingConsole/web/data/lingconsole.db"
                """;
    }
}
