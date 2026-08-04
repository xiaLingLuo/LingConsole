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
    
    private static final int PASSPORT_MAX_PER_MINUTE = 10;
    private final Map<String, java.util.ArrayDeque<Long>> passportAttempts = new java.util.concurrent.ConcurrentHashMap<>();

    public TerminalController(NodeService nodeService, TerminalTicketStore ticketStore) {
        this.nodeService = nodeService;
        this.ticketStore = ticketStore;
    }

    public void register(RoutesConfig routes, String prefix) {
        routes.post(prefix + "/nodes/{nodeId}/terminal/passport", this::passport);
    }

    
    private void passport(Context ctx) {
        if (!allowPassport(ctx.ip())) {
            throw ApiException.badRequest("终端票据申请过于频繁, 请稍后重试");
        }
        String nodeId = ctx.pathParam("nodeId");
        Node node = nodeService.findById(nodeId).orElse(null);
        if (node == null) {
            throw ApiException.notFound("节点不存在");
        }
        PassportRequest req = ctx.bodyAsClass(PassportRequest.class);
        String appId = req.appId() == null || req.appId().isBlank() ? "" : req.appId();

        
        if (!appId.isBlank()) {
            PermissionMiddleware.requirePermission(ctx,
                    "lingconsole.terminal.app." + nodeId + "." + appId);
        } else {
            PermissionMiddleware.requirePermission(ctx, "lingconsole.terminal.node." + nodeId);
        }

        
        User user = AuthMiddleware.currentUser(ctx);
        if (user == null) {
            throw ApiException.unauthorized("未登录");
        }

        String ticket = ticketStore.issue(user.getId(), nodeId, appId);
        ctx.json(im.xz.cn.lingconsole.common.model.ApiResponse.ok(Map.of(
                "ticket", ticket)));
    }

    public record PassportRequest(@JsonProperty("appId") String appId,
                                  @JsonProperty("cols") int cols,
                                  @JsonProperty("rows") int rows) {
    }

    
    private boolean allowPassport(String ip) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;
        java.util.ArrayDeque<Long> deque = passportAttempts.computeIfAbsent(
                ip == null ? "" : ip, k -> new java.util.ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
            }
            if (deque.size() >= PASSPORT_MAX_PER_MINUTE) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
