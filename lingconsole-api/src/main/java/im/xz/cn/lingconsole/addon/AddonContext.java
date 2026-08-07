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
package im.xz.cn.lingconsole.addon;
import im.xz.cn.lingconsole.addon.service.AppService;
import im.xz.cn.lingconsole.addon.service.ConfigService;
import im.xz.cn.lingconsole.addon.service.DataService;
import im.xz.cn.lingconsole.addon.service.ExecService;
import im.xz.cn.lingconsole.addon.service.FileService;
import im.xz.cn.lingconsole.addon.service.LogService;
import im.xz.cn.lingconsole.addon.service.MonitorService;
import im.xz.cn.lingconsole.addon.service.NodeService;
import im.xz.cn.lingconsole.addon.service.UserService;

import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;

public interface AddonContext {
    String PUBLIC = "*";
    AddonInfo info();
    AddonLogger logger();
    NodeService nodes();
    AppService apps();
    FileService files();
    MonitorService monitor();
    ExecService exec();
    DataService data();
    UserService users();
    LogService logs();
    ConfigService config();

    default void registerPanelRoute(AddonRouteMethod method, String path, AddonRouteHandler handler) {
        registerPanelRoute(method, path, handler, null);
    }

    
    void registerPanelRoute(AddonRouteMethod method, String path, AddonRouteHandler handler,
                            String requiredPermission);

    
    void registerDaemonRoute(AddonRouteMethod method, String path, AddonRouteHandler handler);
    void registerCommand(String command, CommandHandler handler);
    void registerPermission(String key, String label);
    void registerSocketEvent(String namespace, String event, String requiredPermission,
                             AddonSocketHandler handler);

    
    default void registerPanelProxy(String mountPath, String scheme, String host, int port, String basePath) {
        registerPanelProxy(mountPath, scheme, host, port, basePath, (String) null);
    }
    
    void registerPanelProxy(String mountPath, String scheme, String host, int port, String basePath,
                            String requiredPermission);

    default void registerPanelProxy(String mountPath, String scheme, String host, int port, String basePath,
                                    java.util.Set<String> forwardHeaders) {
        registerPanelProxy(mountPath, scheme, host, port, basePath, null, forwardHeaders);
    }
    void registerPanelProxy(String mountPath, String scheme, String host, int port, String basePath,
                            String requiredPermission, java.util.Set<String> forwardHeaders);
    void registerPanelMenu(String label, String url);
    
    default AddonRouteHandler panelRouteHandler(String method, String path) {
        return null;
    }
    default String panelRoutePermission(String method, String path) {
        return null;
    }
    default AddonRouteHandler daemonRouteHandler(String method, String path) {
        return null;
    }

    ScheduledExecutorService scheduler();
    Path dataDir();
    Path addonDataDir();
}
