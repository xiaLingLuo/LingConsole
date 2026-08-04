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
import im.xz.cn.lingconsole.app.panel.remote.DaemonConnection;
import im.xz.cn.lingconsole.app.panel.service.LogService;
import im.xz.cn.lingconsole.app.panel.service.NodeService;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import im.xz.cn.lingconsole.common.permission.Permissions;
import im.xz.cn.lingconsole.common.util.ErrorMessageUtil;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;


public class NodeController {

    private final NodeService nodeService;
    private final LogService logService;
    private final im.xz.cn.lingconsole.app.panel.service.NodePermissionIndex nodePermissionIndex;

    public NodeController(NodeService nodeService, LogService logService,
                          im.xz.cn.lingconsole.app.panel.service.NodePermissionIndex nodePermissionIndex) {
        this.nodeService = nodeService;
        this.logService = logService;
        this.nodePermissionIndex = nodePermissionIndex;
    }

    public void register(RoutesConfig routes, String prefix) {
        routes.get(prefix + "/nodes", this::list);
        routes.post(prefix + "/nodes", this::create);
        routes.get(prefix + "/nodes/{id}", this::get);
        routes.put(prefix + "/nodes/{id}", this::update);
        routes.delete(prefix + "/nodes/{id}", this::delete);
        routes.get(prefix + "/nodes/{id}/connect", this::connect);
        routes.put(prefix + "/nodes/{id}/style", this::updateStyle);
    }

    
    private void list(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.NODE_READ);
        List<Node> nodes = nodeService.list();
        ctx.json(ApiResponse.ok(nodes));
    }

    
    private void create(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.NODE_WRITE);
        User user = AuthMiddleware.currentUser(ctx);
        NodeRequest req = ctx.bodyAsClass(NodeRequest.class);
        try {
            Node node = nodeService.create(req.id(), req.name(), req.url(), req.key());
            nodePermissionIndex.refreshNodes();
            nodePermissionIndex.refreshAppsAsync(node.getId());
            logService.record(user.getId(), "node.create", node.getName(), "创建节点", ctx.ip());
            ctx.json(ApiResponse.ok(node));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(ErrorMessageUtil.friendly(e));
        }
    }

    
    private void get(Context ctx) {
        String id = ctx.pathParam("id");
        PermissionMiddleware.requirePermission(ctx, "lingconsole.node.read." + id);
        Node node = nodeService.findById(id)
                .orElseThrow(() -> new RuntimeException("节点不存在"));
        ctx.json(ApiResponse.ok(node));
    }

    
    private void update(Context ctx) {
        String id = ctx.pathParam("id");
        PermissionMiddleware.requirePermission(ctx, "lingconsole.node.write." + id);
        User user = AuthMiddleware.currentUser(ctx);
        NodeRequest req = ctx.bodyAsClass(NodeRequest.class);
        
        if (req.url() != null || (req.key() != null && !req.key().isBlank())) {
            PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
        }
        Node node;
        try {
            node = nodeService.update(id, req.name(), req.url(), req.key());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(ErrorMessageUtil.friendly(e));
        }
        if (node == null) {
            ctx.status(404);
            ctx.json(ApiResponse.error(404, "节点不存在"));
            return;
        }
        nodePermissionIndex.refreshNodes();
        nodePermissionIndex.refreshAppsAsync(id);
        logService.record(user.getId(), "node.update", node.getName(), "更新节点", ctx.ip());
        ctx.json(ApiResponse.ok(node));
    }

    
    private void delete(Context ctx) {
        String id = ctx.pathParam("id");
        PermissionMiddleware.requirePermission(ctx, "lingconsole.node.write." + id);
        User user = AuthMiddleware.currentUser(ctx);
        nodeService.delete(id);
        nodePermissionIndex.refreshNodes();
        logService.record(user.getId(), "node.delete", id, "删除节点", ctx.ip());
        ctx.json(ApiResponse.ok());
    }

    
    private void connect(Context ctx) {
        String id = ctx.pathParam("id");
        PermissionMiddleware.requirePermission(ctx, "lingconsole.node.read." + id);
        Node node = nodeService.findById(id).orElse(null);
        if (node == null) {
            ctx.status(404);
            ctx.json(ApiResponse.error(404, "节点不存在"));
            return;
        }
        DaemonConnection conn = nodeService.getConnection(id);
        boolean online = conn != null && conn.isConnected();
        ctx.json(ApiResponse.ok(Map.of(
                "online", online,
                "name", node.getName())));
    }

    
    private void updateStyle(Context ctx) {
        String id = ctx.pathParam("id");
        PermissionMiddleware.requirePermission(ctx, "lingconsole.node.write." + id);
        User user = AuthMiddleware.currentUser(ctx);
        StyleRequest req = ctx.bodyAsClass(StyleRequest.class);
        if (req.style() == null || !java.util.Set.of("auto", "windows", "linux").contains(req.style())) {
            throw im.xz.cn.lingconsole.app.panel.exception.ApiException.badRequest("无效的系统偏好, 仅支持 auto/windows/linux");
        }
        Node node = nodeService.updateStyle(id, req.style());
        if (node == null) {
            ctx.status(404);
            ctx.json(ApiResponse.error(404, "节点不存在"));
            return;
        }
        logService.record(user.getId(), "node.updateStyle", node.getName(), "系统偏好: " + req.style(), ctx.ip());
        ctx.json(ApiResponse.ok(node));
    }

    public record NodeRequest(@JsonProperty("id") String id,
                              @JsonProperty("name") String name,
                              @JsonProperty("url") String url,
                              @JsonProperty("key") String key) {
    }

    public record StyleRequest(@JsonProperty("style") String style) {
    }
}

