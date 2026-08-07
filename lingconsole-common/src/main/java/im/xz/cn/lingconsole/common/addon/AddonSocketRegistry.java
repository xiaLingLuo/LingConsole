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

import im.xz.cn.lingconsole.addon.AddonSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


public final class AddonSocketRegistry {

    public record Registration(String owner, String namespace, String event, String requiredPermission,
                               AddonSocketHandler handler) {
    }

    private final Map<String, List<Registration>> byAddon = new ConcurrentHashMap<>();

    public void register(String addonName, String namespace, String event, String requiredPermission,
                         AddonSocketHandler handler) {
        if (addonName == null || addonName.isBlank() || namespace == null || namespace.isBlank()
                || event == null || event.isBlank() || requiredPermission == null
                || requiredPermission.isBlank() || handler == null) {
            throw new IllegalArgumentException("addonName, namespace, event, requiredPermission and handler are required");
        }
        byAddon.computeIfAbsent(addonName, k -> new CopyOnWriteArrayList<>())
                .add(new Registration(addonName, namespace, event, requiredPermission, handler));
    }

    
    public List<Registration> all() {
        return byAddon.values().stream().flatMap(List::stream).toList();
    }

    
    public List<Registration> all(String addonName) {
        List<Registration> list = byAddon.get(addonName);
        return list == null ? List.of() : List.copyOf(list);
    }

    public java.util.Set<String> addonNames() {
        return java.util.Set.copyOf(byAddon.keySet());
    }

    
    public void clear(String addonName) {
        byAddon.remove(addonName);
    }

    public Registration find(String addonName, String namespace, String event) {
        return all(addonName).stream()
                .filter(reg -> reg.namespace().equals(namespace) && reg.event().equals(event))
                .reduce((first, second) -> second)
                .orElse(null);
    }
}
