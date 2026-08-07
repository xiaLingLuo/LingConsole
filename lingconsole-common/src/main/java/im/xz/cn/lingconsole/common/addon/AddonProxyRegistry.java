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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AddonProxyRegistry {

    private final Map<String, List<AddonProxy>> byAddon = new ConcurrentHashMap<>();

    public void register(String addonName, String mountPath, String scheme,
                         String host, int port, String basePath, String requiredPermission) {
        register(addonName, mountPath, scheme, host, port, basePath, requiredPermission, null);
    }

    public void register(String addonName, String mountPath, String scheme,
                         String host, int port, String basePath, String requiredPermission,
                         Set<String> forwardHeaders) {
        if (addonName == null || mountPath == null || host == null) {
            return;
        }
        byAddon.computeIfAbsent(addonName, k -> new CopyOnWriteArrayList<>())
                .add(new AddonProxy(addonName, mountPath,
                        scheme == null || scheme.isBlank() ? "http" : scheme,
                        host, port, basePath == null ? "" : basePath, requiredPermission,
                        forwardHeaders == null ? Set.of() : Set.copyOf(forwardHeaders)));
    }

    public void clear(String addonName) {
        byAddon.remove(addonName);
    }

    public List<AddonProxy> list() {
        return byAddon.values().stream().flatMap(List::stream).toList();
    }

    public AddonProxy find(String addonName, String requestPath) {
        List<AddonProxy> proxies = byAddon.get(addonName);
        if (proxies == null || requestPath == null) return null;
        return proxies.stream()
                .filter(proxy -> requestPath.equals(proxy.mountPath())
                        || requestPath.startsWith(proxy.mountPath() + "/"))
                .max(java.util.Comparator.comparingInt(proxy -> proxy.mountPath().length()))
                .orElse(null);
    }
}
