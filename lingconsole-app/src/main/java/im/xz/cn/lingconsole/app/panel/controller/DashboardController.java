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

import im.xz.cn.lingconsole.app.panel.middleware.AuthMiddleware;
import im.xz.cn.lingconsole.app.panel.model.Node;
import im.xz.cn.lingconsole.app.panel.service.NodeService;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import im.xz.cn.lingconsole.common.model.SystemInfo;
import im.xz.cn.lingconsole.common.permission.Permissions;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;


public class DashboardController {

    private final NodeService nodeService;

    public DashboardController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void register(RoutesConfig routes, String prefix) {
        routes.get(prefix + "/dashboard/stats", this::stats);
        routes.get(prefix + "/dashboard/nodes-status", this::nodesStatus);
    }

    
    private void stats(Context ctx) {
        List<Node> nodes = nodeService.list();
        long online = nodes.stream().filter(n -> n.getStatus() == Node.STATUS_ONLINE).count();

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("nodeCount", nodes.size());
        data.put("nodeOnline", online);

        
        if (AuthMiddleware.authUser(ctx) != null
                && AuthMiddleware.authUser(ctx).hasPermission(Permissions.SYSTEM_STATUS)) {
            Object systemInfo = null;
            Node onlineNode = nodes.stream().filter(n -> n.getStatus() == Node.STATUS_ONLINE).findFirst().orElse(null);
            if (onlineNode != null) {
                try {
                    systemInfo = nodeService.systemInfo(onlineNode.getId());
                } catch (Exception _) {
                    
                }
            }
            if (systemInfo == null) {
                systemInfo = SystemInfo.collect();
            }
            data.put("systemInfo", systemInfo);
        } else {
            data.put("systemInfo", null);
        }

        ctx.json(ApiResponse.ok(data));
    }

    
    private void nodesStatus(Context ctx) {
        if (AuthMiddleware.authUser(ctx) != null
                && AuthMiddleware.authUser(ctx).hasPermission(Permissions.NODE_READ)) {
            ctx.json(ApiResponse.ok(nodeService.list()));
        } else {
            ctx.json(ApiResponse.ok(List.of()));
        }
    }
}
