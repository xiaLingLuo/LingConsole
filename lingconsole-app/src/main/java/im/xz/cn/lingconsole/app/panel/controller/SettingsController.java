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
package im.xz.cn.lingconsole.app.panel.controller;

import im.xz.cn.lingconsole.app.panel.PanelConfig;
import im.xz.cn.lingconsole.app.panel.middleware.AuthMiddleware;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import im.xz.cn.lingconsole.daemon.DaemonConfig;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;


public class SettingsController {

    private final PanelConfig panelConfig;
    private final DaemonConfig daemonConfig;

    public SettingsController(PanelConfig panelConfig, DaemonConfig daemonConfig) {
        this.panelConfig = panelConfig;
        this.daemonConfig = daemonConfig;
    }

    public void register(RoutesConfig routes, String prefix) {
        routes.get(prefix + "/settings", this::get);
    }

    
    private void get(Context ctx) {
        if (AuthMiddleware.currentUser(ctx) == null) {
            throw im.xz.cn.lingconsole.app.panel.exception.ApiException.unauthorized("未登录");
        }
        boolean privileged = AuthMiddleware.authUser(ctx) != null
                && AuthMiddleware.authUser(ctx).hasPermission(im.xz.cn.lingconsole.common.permission.Permissions.PERMISSION_ASSIGN);

        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("host", panelConfig.host());
        panel.put("port", panelConfig.port());
        panel.put("theme", panelConfig.theme());
        panel.put("language", panelConfig.language());
        if (privileged) {
            panel.put("sessionTimeout", panelConfig.sessionTimeout());
            panel.put("maxLoginAttempts", panelConfig.maxLoginAttempts());
            panel.put("lockoutDuration", panelConfig.lockoutDuration());
            panel.put("rateLimitPerSecond", panelConfig.rateLimitPerSecond());
        }

        Map<String, Object> daemon = new LinkedHashMap<>();
        if (privileged) {
            daemon.put("host", daemonConfig.host());
            daemon.put("port", daemonConfig.port());
            daemon.put("name", daemonConfig.name());
            daemon.put("whiteListEnabled", daemonConfig.whiteListEnabled());
            daemon.put("whiteListIps", daemonConfig.whiteListIps());
            daemon.put("authTimeout", daemonConfig.authTimeout());
            daemon.put("maxFileTasks", daemonConfig.maxFileTasks());
            daemon.put("maxZipSize", daemonConfig.maxZipSize());
            daemon.put("outputBufferSize", daemonConfig.outputBufferSize());
            daemon.put("softShutdownEnabled", daemonConfig.softShutdownEnabled());
            daemon.put("softShutdownWaitSeconds", daemonConfig.softShutdownWaitSeconds());
        }

        ctx.json(ApiResponse.ok(Map.of(
                "panel", panel,
                "daemon", daemon)));
    }
}
