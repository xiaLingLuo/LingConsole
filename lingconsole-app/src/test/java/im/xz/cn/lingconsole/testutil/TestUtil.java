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
package im.xz.cn.lingconsole.testutil;

import im.xz.cn.lingconsole.app.panel.PanelConfig;
import im.xz.cn.lingconsole.daemon.DaemonConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;


public final class TestUtil {

    public static final String TEST_DAEMON_KEY = "test-key-0123456789abcdef";

    private TestUtil() {
    }

    
    public static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    
    public static DaemonConfig writeDaemonConfig(Path dataDir, int port) throws IOException {
        Path dir = dataDir.resolve("damon");
        Files.createDirectories(dir);
        Path configFile = dir.resolve("config.toml");
        String appsDir = dataDir.resolve("apps").toString().replace("\\", "\\\\");
        Files.writeString(configFile, """
                [server]
                host = "127.0.0.1"
                port = %d

                [auth]
                key = "%s"
                name = "test-daemon"

                [instance]
                defaultAppPath = "%s"
                """.formatted(port, TEST_DAEMON_KEY, appsDir));
        return DaemonConfig.load(configFile);
    }

    
    public static PanelConfig writePanelConfig(Path dataDir, int port) throws IOException {
        Path dir = dataDir.resolve("web");
        Files.createDirectories(dir.resolve("data"));
        Path configFile = dir.resolve("config.toml");
        String dbPath = dir.resolve("data").resolve("test.db").toString().replace("\\", "\\\\");
        String firstPasswordFile = dataDir.resolve("first-launch-password.txt")
                .toString().replace("\\", "\\\\");
        Files.writeString(configFile, """
                [server]
                host = "127.0.0.1"
                port = %d

                [security]
                rateLimitPerSecond = 100000
                firstLaunchPasswordFile = "%s"

                [database]
                path = "%s"
                """.formatted(port, firstPasswordFile, dbPath));
        return PanelConfig.load(configFile);
    }
}
