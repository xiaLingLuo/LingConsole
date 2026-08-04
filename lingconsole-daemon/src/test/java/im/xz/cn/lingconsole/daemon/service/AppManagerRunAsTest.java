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
package im.xz.cn.lingconsole.daemon.service;

import im.xz.cn.lingconsole.daemon.model.AppInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppManagerRunAsTest {

    @TempDir
    Path tempDir;

    private AppManager newManager() {
        return new AppManager(tempDir.resolve("apps").toString());
    }

    @Test
    void runAsUserSetClearAndPersist() {
        AppManager mgr = newManager();
        mgr.create("runasapp", "测试", "echo hi", "general", false, false, 3, List.of(), Map.of());

        AppInfo updated = mgr.update("runasapp", null, null, null, false, false, 0,
                null, null, null, null, null, "www-data");
        assertEquals("www-data", updated.getRunAsUser());

        AppInfo reloaded = mgr.get("runasapp");
        assertEquals("www-data", reloaded.getRunAsUser(), "重启后应保留启动用户配置");

        AppInfo cleared = mgr.update("runasapp", null, null, null, false, false, 0,
                null, null, null, null, null, "");
        assertEquals("", cleared.getRunAsUser());
    }

    @Test
    void runAsUserRejectsDangerousInput() {
        AppManager mgr = newManager();
        mgr.create("runasapp2", "测试", "echo hi", "general", false, false, 3, List.of(), Map.of());

        assertThrows(IllegalArgumentException.class, () ->
                mgr.update("runasapp2", null, null, null, false, false, 0,
                        null, null, null, null, null, "evil; rm -rf"));
        assertThrows(IllegalArgumentException.class, () ->
                mgr.update("runasapp2", null, null, null, false, false, 0,
                        null, null, null, null, null, "../root"));
        assertThrows(IllegalArgumentException.class, () ->
                mgr.update("runasapp2", null, null, null, false, false, 0,
                        null, null, null, null, null, "-u root"));
    }
}
