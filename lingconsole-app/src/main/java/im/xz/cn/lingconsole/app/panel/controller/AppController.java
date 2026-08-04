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
import com.fasterxml.jackson.databind.JsonNode;
import im.xz.cn.lingconsole.app.panel.exception.ApiException;
import im.xz.cn.lingconsole.app.panel.middleware.AuthMiddleware;
import im.xz.cn.lingconsole.app.panel.middleware.PermissionMiddleware;
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.app.panel.remote.DaemonConnection;
import im.xz.cn.lingconsole.app.panel.service.LogService;
import im.xz.cn.lingconsole.app.panel.service.NodeService;
import im.xz.cn.lingconsole.common.util.ErrorMessageUtil;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class AppController {

    private final NodeService nodeService;
    private final LogService logService;
    private final im.xz.cn.lingconsole.app.panel.service.NodePermissionIndex nodePermissionIndex;

    public AppController(NodeService nodeService, LogService logService,
                         im.xz.cn.lingconsole.app.panel.service.NodePermissionIndex nodePermissionIndex) {
        this.nodeService = nodeService;
        this.logService = logService;
        this.nodePermissionIndex = nodePermissionIndex;
    }

    public void register(RoutesConfig routes, String prefix) {
        routes.get(prefix + "/nodes/{nodeId}/apps", this::list);
        routes.post(prefix + "/nodes/{nodeId}/apps", this::create);
        routes.get(prefix + "/nodes/{nodeId}/apps/{appId}", this::get);
        routes.put(prefix + "/nodes/{nodeId}/apps/{appId}", this::update);
        routes.delete(prefix + "/nodes/{nodeId}/apps/{appId}", this::delete);
        routes.post(prefix + "/nodes/{nodeId}/apps/{appId}/start", this::start);
        routes.post(prefix + "/nodes/{nodeId}/apps/{appId}/stop", this::stop);
        routes.post(prefix + "/nodes/{nodeId}/apps/{appId}/restart", this::restart);
        routes.get(prefix + "/nodes/{nodeId}/apps/{appId}/logs", this::logs);
        
        routes.get(prefix + "/nodes/{nodeId}/apps/{appId}/advanced", this::advancedGet);
        routes.put(prefix + "/nodes/{nodeId}/apps/{appId}/advanced", this::advancedUpdate);
    }

    
    private void list(Context ctx) {
        String nodeId = ctx.pathParam("nodeId");
        PermissionMiddleware.requirePermission(ctx, "lingconsole.app.read." + nodeId);
        im.xz.cn.lingconsole.app.panel.model.AuthUser auth = AuthMiddleware.authUser(ctx);
        Object result = proxy(ctx, "app:list", Map.of());
        
        if (auth != null && result instanceof com.fasterxml.jackson.databind.JsonNode arr
                && arr.isArray()) {
            java.util.List<Object> filtered = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : arr) {
                if (auth.hasPermission("lingconsole.app.read." + nodeId + "." + item.path("id").asText())) {
                    filtered.add(sanitizeAppInfo(auth, nodeId, item));
                }
            }
            result = filtered;
        }
        ctx.json(ok(result));
    }

    
    private void create(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, "lingconsole.app.write." + ctx.pathParam("nodeId"));
        User user = AuthMiddleware.currentUser(ctx);
        AppRequest req = ctx.bodyAsClass(AppRequest.class);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", req.id());
        payload.put("name", req.name());
        payload.put("command", req.command());
        payload.put("type", req.type() == null ? "general" : req.type());
        payload.put("autoStart", req.autoStart());
        payload.put("autoRestart", req.autoRestart());
        payload.put("maxRestartCount", req.maxRestartCount());
        payload.put("args", req.args() == null ? List.of() : req.args());
        payload.put("environment", req.environment() == null ? Map.of() : req.environment());
        Object result = proxy(ctx, "app:create", payload);
        nodePermissionIndex.refreshAppsAsync(ctx.pathParam("nodeId"));
        logService.record(user.getId(), "app.create", req.name(), "创建应用", ctx.ip());
        ctx.json(ok(result));
    }

    
    private void get(Context ctx) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.read." + ctx.pathParam("nodeId") + "." + ctx.pathParam("appId"));
        Object result = proxy(ctx, "app:get", Map.of("id", ctx.pathParam("appId")));
        ctx.json(ok(result));
    }

    
    private com.fasterxml.jackson.databind.JsonNode sanitizeAppInfo(
            im.xz.cn.lingconsole.app.panel.model.AuthUser auth, String nodeId,
            com.fasterxml.jackson.databind.JsonNode item) {
        if (item == null || !item.isObject()) {
            return item;
        }
        String appId = item.path("id").asText("");
        boolean privileged = auth.isRoot()
                || auth.hasPermission("lingconsole.app.advanced." + nodeId + "." + appId);
        if (privileged) {
            return item;
        }
        var copy = ((com.fasterxml.jackson.databind.node.ObjectNode) item).deepCopy();
        copy.remove("environment");
        copy.remove("workDir");
        copy.remove("runAsUser");
        return copy;
    }

    
    private void update(Context ctx) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.write." + ctx.pathParam("nodeId") + "." + ctx.pathParam("appId"));
        User user = AuthMiddleware.currentUser(ctx);
        AppRequest req = ctx.bodyAsClass(AppRequest.class);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", ctx.pathParam("appId"));
        payload.put("name", req.name());
        payload.put("command", req.command());
        payload.put("type", req.type());
        payload.put("autoStart", req.autoStart());
        payload.put("autoRestart", req.autoRestart());
        payload.put("maxRestartCount", req.maxRestartCount());
        if (req.args() != null) {
            payload.put("args", req.args());
        }
        if (req.environment() != null) {
            payload.put("environment", req.environment());
        }
        Object result = proxy(ctx, "app:update", payload);
        nodePermissionIndex.refreshAppsAsync(ctx.pathParam("nodeId"));
        logService.record(user.getId(), "app.update", ctx.pathParam("appId"), "更新应用", ctx.ip());
        ctx.json(ok(result));
    }

    
    private void delete(Context ctx) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.write." + ctx.pathParam("nodeId") + "." + ctx.pathParam("appId"));
        User user = AuthMiddleware.currentUser(ctx);
        Object result = proxy(ctx, "app:delete", Map.of("id", ctx.pathParam("appId")));
        nodePermissionIndex.refreshAppsAsync(ctx.pathParam("nodeId"));
        logService.record(user.getId(), "app.delete", ctx.pathParam("appId"), "删除应用", ctx.ip());
        ctx.json(ok(result));
    }

    
    private void start(Context ctx) {
        control(ctx, "start");
    }

    private void stop(Context ctx) {
        control(ctx, "stop");
    }

    private void restart(Context ctx) {
        control(ctx, "restart");
    }

    private void control(Context ctx, String action) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.write." + ctx.pathParam("nodeId") + "." + ctx.pathParam("appId"));
        User user = AuthMiddleware.currentUser(ctx);
        Object result = proxy(ctx, "app:" + action, Map.of("id", ctx.pathParam("appId")));
        logService.record(user.getId(), "app." + action, ctx.pathParam("appId"), "应用" + action, ctx.ip());
        ctx.json(ok(result));
    }

    
    private void logs(Context ctx) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.read." + ctx.pathParam("nodeId") + "." + ctx.pathParam("appId"));
        int count = ctx.queryParamAsClass("count", Integer.class).getOrDefault(200);
        Object result = proxy(ctx, "app:log", Map.of("id", ctx.pathParam("appId"), "count", count));
        ctx.json(ok(result));
    }

    
    private void advancedGet(Context ctx) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.advanced." + ctx.pathParam("nodeId") + "." + ctx.pathParam("appId"));
        Object result = proxy(ctx, "app:get", Map.of("id", ctx.pathParam("appId")));
        ctx.json(ok(result));
    }

    
    private void advancedUpdate(Context ctx) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.advanced." + ctx.pathParam("nodeId") + "." + ctx.pathParam("appId"));
        User user = AuthMiddleware.currentUser(ctx);
        AdvancedRequest req = ctx.bodyAsClass(AdvancedRequest.class);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", ctx.pathParam("appId"));
        payload.put("name", req.name());
        payload.put("command", req.command());
        payload.put("type", req.type());
        payload.put("autoStart", req.autoStart());
        payload.put("autoRestart", req.autoRestart());
        payload.put("maxRestartCount", req.maxRestartCount());
        payload.put("workDir", req.workDir());
        payload.put("encoding", req.encoding());
        payload.put("ptyType", req.ptyType());
        payload.put("runAsUser", req.runAsUser() == null ? "" : req.runAsUser());
        if (req.args() != null) {
            payload.put("args", req.args());
        }
        if (req.environment() != null) {
            payload.put("environment", req.environment());
        }
        Object result = proxy(ctx, "app:update", payload);
        logService.record(user.getId(), "app.advancedUpdate", ctx.pathParam("appId"), "高级配置更新", ctx.ip());
        ctx.json(ok(result));
    }

    
    
    

    private Object proxy(Context ctx, String event, Map<String, Object> requestData) {
        String nodeId = ctx.pathParam("nodeId");
        DaemonConnection conn = nodeService.getConnection(nodeId);
        if (conn == null) {
            throw ApiException.badRequest("节点离线或未连接, 请确保 55700 端口可被访问");
        }
        try {
            Object resp = conn.requestBlocking(event,
                    requestData == null ? Map.of() : requestData, 8000);
            if (resp instanceof JsonNode n) {
                int status = n.path("status").asInt(-1);
                if (status == 200) {
                    return n.path("data");
                }
                throw ApiException.badRequest(n.path("message").asText("操作失败"));
            }
            throw ApiException.badRequest("节点返回无效响应");
        } catch (java.util.concurrent.TimeoutException e) {
            throw ApiException.badRequest("节点响应超时");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorMessageUtil.with("操作失败", e));
        }
    }

    private im.xz.cn.lingconsole.common.model.ApiResponse ok(Object data) {
        return im.xz.cn.lingconsole.common.model.ApiResponse.ok(data);
    }

    public record AppRequest(@JsonProperty("id") String id,
                             @JsonProperty("name") String name,
                             @JsonProperty("command") String command,
                             @JsonProperty("type") String type,
                             @JsonProperty("autoStart") boolean autoStart,
                             @JsonProperty("autoRestart") boolean autoRestart,
                             @JsonProperty("maxRestartCount") int maxRestartCount,
                             @JsonProperty("args") List<String> args,
                             @JsonProperty("environment") Map<String, String> environment) {
    }

    
    public record AdvancedRequest(@JsonProperty("name") String name,
                                  @JsonProperty("command") String command,
                                  @JsonProperty("type") String type,
                                  @JsonProperty("autoStart") boolean autoStart,
                                  @JsonProperty("autoRestart") boolean autoRestart,
                                  @JsonProperty("maxRestartCount") int maxRestartCount,
                                  @JsonProperty("workDir") String workDir,
                                  @JsonProperty("encoding") String encoding,
                                  @JsonProperty("ptyType") String ptyType,
                                  @JsonProperty("runAsUser") String runAsUser,
                                  @JsonProperty("args") List<String> args,
                                  @JsonProperty("environment") Map<String, String> environment) {
    }
}
