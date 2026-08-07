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
import im.xz.cn.lingconsole.app.panel.exception.ApiException;
import im.xz.cn.lingconsole.app.panel.middleware.AuthMiddleware;
import im.xz.cn.lingconsole.app.panel.middleware.PermissionMiddleware;
import im.xz.cn.lingconsole.app.panel.model.Node;
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.app.panel.service.NodeService;
import im.xz.cn.lingconsole.app.panel.service.TerminalTicketStore;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.Map;


public class TerminalController {

    private final NodeService nodeService;
    private final TerminalTicketStore ticketStore;
    public TerminalController(NodeService nodeService, TerminalTicketStore ticketStore) {
        this.nodeService = nodeService;
        this.ticketStore = ticketStore;
    }

    public void register(RoutesConfig routes, String prefix) {
        routes.post(prefix + "/nodes/{nodeId}/terminal/passport", this::passport);
    }

    
    private void passport(Context ctx) {
        String nodeId = ctx.pathParam("nodeId");
        Node node = nodeService.findById(nodeId).orElse(null);
        if (node == null) {
            throw ApiException.notFound("节点不存在");
        }
        PassportRequest req = ctx.bodyAsClass(PassportRequest.class);
        String appId = req.appId() == null || req.appId().isBlank() ? "" : req.appId();

        
        if (!appId.isBlank()) {
            im.xz.cn.lingconsole.app.panel.service.NodePermissionIndex.requireCurrentOwnership(nodeId, appId);
            PermissionMiddleware.requirePermission(ctx,
                    "lingconsole.terminal.app." + appId);
        } else {
            PermissionMiddleware.requirePermission(ctx, "lingconsole.terminal.node." + nodeId);
        }

        
        User user = AuthMiddleware.currentUser(ctx);
        if (user == null) {
            throw ApiException.unauthorized("未登录");
        }

        String ticket;
        try {
            ticket = ticketStore.issue(user.getId(), nodeId, appId, ctx.ip());
        } catch (TerminalTicketStore.LimitExceededException e) {
            ctx.status(429);
            ctx.json(im.xz.cn.lingconsole.common.model.ApiResponse.error(429, e.getMessage()));
            return;
        }
        ctx.json(im.xz.cn.lingconsole.common.model.ApiResponse.ok(Map.of(
                "ticket", ticket)));
    }

    public record PassportRequest(@JsonProperty("appId") String appId,
                                  @JsonProperty("cols") int cols,
                                  @JsonProperty("rows") int rows) {
    }

}
