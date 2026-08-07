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

import im.xz.cn.lingconsole.app.panel.middleware.PermissionMiddleware;
import im.xz.cn.lingconsole.app.panel.repository.LogRepository;
import im.xz.cn.lingconsole.app.panel.service.LogService;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import im.xz.cn.lingconsole.common.permission.Permissions;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;


public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }
    public void register(RoutesConfig routes, String prefix) {
        routes.get(prefix + "/logs", this::list);
    }
    
    private void list(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.LOG_READ);
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
        int pageSize = ctx.queryParamAsClass("pageSize", Integer.class).getOrDefault(LogService.DEFAULT_PAGE_SIZE);
        LogRepository.Query query = new LogRepository.Query(
                ctx.queryParam("q"), ctx.queryParam("sourceType"), ctx.queryParam("pluginName"),
                ctx.queryParam("userId"), ctx.queryParam("nodeId"), ctx.queryParam("appId"),
                ctx.queryParam("action"), ctx.queryParam("requestId"), longParam(ctx, "startTime"),
                longParam(ctx, "endTime"));
        LogService.Page result = logService.list(query, page, pageSize);
        Map<String, Object> filters = createFilters(result);
        ctx.json(ApiResponse.ok(Map.of(
                "logs", result.logs(),
                "filters", filters,
                "page", result.page(),
                "pageSize", result.pageSize(),
                "total", result.total(),
                "totalPages", result.totalPages())));
    }

    @NotNull
    private static Map<String, Object> createFilters(LogService.Page result) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("q", result.filters().q());
        filters.put("sourceType", result.filters().sourceType());
        filters.put("pluginName", result.filters().pluginName());
        filters.put("userId", result.filters().userId());
        filters.put("nodeId", result.filters().nodeId());
        filters.put("appId", result.filters().appId());
        filters.put("action", result.filters().action());
        filters.put("requestId", result.filters().requestId());
        filters.put("startTime", result.filters().startTime());
        filters.put("endTime", result.filters().endTime());
        return filters;
    }

    private static Long longParam(Context ctx, String name) {
        String value = ctx.queryParam(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
