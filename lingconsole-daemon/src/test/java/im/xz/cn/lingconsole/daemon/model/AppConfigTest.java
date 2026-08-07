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
package im.xz.cn.lingconsole.daemon.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void toTomlRoundTrip() throws Exception {
        AppConfig cfg = new AppConfig();
        cfg.setId("abc-123");
        cfg.setName("测试应用");
        cfg.setType("general");
        cfg.setAutoStart(true);
        cfg.setAutoRestart(true);
        cfg.setMaxRestartCount(5);
        cfg.setCommand("java -jar server.jar");
        cfg.setWorkDir("/opt/app/data");
        cfg.setRunAsUser("minecraft");
        cfg.setArgs(List.of("--port", "25565"));
        cfg.setEnvironment(Map.of("JAVA_HOME", "/usr/lib/jvm/java"));
        cfg.setProtectAppFilesFromSymlinkEscape(false);

        Path file = tempDir.resolve("config.toml");
        Files.writeString(file, cfg.toToml());

        AppConfig loaded = AppConfig.load(file);
        assertEquals("abc-123", loaded.getId());
        assertEquals("测试应用", loaded.getName());
        assertEquals("general", loaded.getType());
        assertTrue(loaded.isAutoStart());
        assertTrue(loaded.isAutoRestart());
        assertEquals(5, loaded.getMaxRestartCount());
        assertEquals("java -jar server.jar", loaded.getCommand());
        assertEquals("/opt/app/data", loaded.getWorkDir());
        assertEquals("minecraft", loaded.getRunAsUser());
        assertEquals(List.of("--port", "25565"), loaded.getArgs());
        assertEquals("/usr/lib/jvm/java", loaded.getEnvironment().get("JAVA_HOME"));
        assertEquals(false, loaded.isProtectAppFilesFromSymlinkEscape());
    }

    @Test
    void defaultValuesWhenMissing() throws Exception {
        Path file = tempDir.resolve("minimal.toml");
        Files.writeString(file, """
                [app]
                id = "x"
                name = "y"
                """);
        AppConfig cfg = AppConfig.load(file);
        assertEquals("general", cfg.getType());
        assertEquals(3, cfg.getMaxRestartCount());
        assertTrue(cfg.getArgs().isEmpty());
        assertTrue(cfg.getEnvironment().isEmpty());
        assertEquals("UTF-8", cfg.getEncoding());
        assertTrue(cfg.isProtectAppFilesFromSymlinkEscape());
    }
}
