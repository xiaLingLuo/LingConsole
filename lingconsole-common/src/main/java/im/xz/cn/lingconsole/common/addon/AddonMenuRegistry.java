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
package im.xz.cn.lingconsole.common.addon;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 插件侧栏菜单注册表 (按插件分组, 支持热重载清理)。
 */
public final class AddonMenuRegistry {

    private final Map<String, List<AddonMenu>> byAddon = new ConcurrentHashMap<>();

    public void register(String addonName, String label, String url) {
        if (addonName == null || label == null || url == null) {
            return;
        }
        byAddon.computeIfAbsent(addonName, k -> new CopyOnWriteArrayList<>())
                .add(new AddonMenu(label, url, "▦"));
    }

    public void clear(String addonName) {
        byAddon.remove(addonName);
    }

    public List<AddonMenu> list() {
        return byAddon.values().stream().flatMap(List::stream).toList();
    }
}
