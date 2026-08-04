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
package im.xz.cn.lingconsole.common.permission;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public final class PluginPermissionRegistry {

    private PluginPermissionRegistry() {
    }

    private static final Map<String, String> PERMISSIONS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> BY_PLUGIN = new ConcurrentHashMap<>();

    public static void register(String pluginName, String key, String label) {
        if (pluginName == null || pluginName.isBlank() || key == null || key.isBlank()) {
            return;
        }
        String full = pluginName + "." + key;
        PERMISSIONS.put(full, label == null || label.isBlank() ? key : label);
        BY_PLUGIN.computeIfAbsent(pluginName, k -> ConcurrentHashMap.newKeySet()).add(full);
    }

    public static void unregisterAll(String pluginName) {
        if (pluginName == null) {
            return;
        }
        Set<String> keys = BY_PLUGIN.remove(pluginName);
        if (keys != null) {
            keys.forEach(PERMISSIONS::remove);
        }
    }

    public static Map<String, String> all() {
        return Map.copyOf(PERMISSIONS);
    }

    public static Set<String> allKeys() {
        return PERMISSIONS.keySet();
    }
}
