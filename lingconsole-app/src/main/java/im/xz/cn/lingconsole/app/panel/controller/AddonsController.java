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

import com.fasterxml.jackson.annotation.JsonProperty;
import im.xz.cn.lingconsole.addon.ConfigEntry;
import im.xz.cn.lingconsole.app.addon.AddonContextImpl;
import im.xz.cn.lingconsole.app.panel.middleware.PermissionMiddleware;
import im.xz.cn.lingconsole.common.addon.AddonManager;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import im.xz.cn.lingconsole.common.permission.Permissions;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class AddonsController {

    private final AddonManager addonManager;

    public AddonsController(AddonManager addonManager) {
        this.addonManager = addonManager;
    }

    public void register(RoutesConfig routes, String prefix) {
        routes.get(prefix + "/addons", this::list);
        routes.put(prefix + "/addons/{name}/config", this::updateConfig);
        routes.post(prefix + "/addons/{name}/reload", this::reload);
    }

    
    private void list(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AddonManager.LoadedAddon la : addonManager.addons()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", la.descriptor().name());
            m.put("version", la.descriptor().version());
            m.put("author", la.descriptor().author());
            m.put("description", la.descriptor().description());
            m.put("state", la.state().name());
            m.put("error", la.error());
            m.put("jar", la.jarPath() == null ? null : la.jarPath().getFileName().toString());
            if (la.context() instanceof AddonContextImpl impl) {
                List<Map<String, Object>> configs = new ArrayList<>();
                for (ConfigEntry e : impl.config().entries()) {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("key", e.key());
                    cm.put("type", e.type().name());
                    cm.put("label", e.label());
                    cm.put("description", e.description());
                    cm.put("default", e.defaultValue());
                    cm.put("options", e.options());
                    cm.put("value", e.value());
                    configs.add(cm);
                }
                m.put("config", configs);
            }
            result.add(m);
        }
        ctx.json(ApiResponse.ok(result));
    }

    
    private void updateConfig(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
        String name = ctx.pathParam("name");
        AddonContextImpl impl = contextImpl(name);
        if (impl == null) {
            ctx.status(404);
            ctx.json(ApiResponse.error(404, "插件不存在或未加载"));
            return;
        }
        ConfigRequest req = ctx.bodyAsClass(ConfigRequest.class);
        Map<String, String> values = new LinkedHashMap<>();
        if (req.values() != null) {
            values.putAll(req.values());
        }
        impl.saveConfig(values);
        boolean reloaded = addonManager.reload(name);
        ctx.json(ApiResponse.ok(Map.of("reloaded", reloaded)));
    }

    
    private void reload(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
        String name = ctx.pathParam("name");
        boolean ok = addonManager.reload(name);
        if (!ok) {
            ctx.status(404);
            ctx.json(ApiResponse.error(404, "插件不存在"));
            return;
        }
        ctx.json(ApiResponse.ok());
    }

    private AddonContextImpl contextImpl(String name) {
        var ctx = addonManager.contextOf(name);
        return ctx instanceof AddonContextImpl impl ? impl : null;
    }

    public record ConfigRequest(@JsonProperty("values") Map<String, String> values) {
    }
}
