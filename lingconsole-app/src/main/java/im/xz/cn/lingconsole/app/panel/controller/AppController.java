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

    private static final java.util.Set<String> ADVANCED_FIELDS = java.util.Set.of(
            "command", "args", "environment", "workDir", "runAsUser", "encoding", "ptyType",
            "protectAppFilesFromSymlinkEscape", "confirmDisableAppFileSymlinkProtection");

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
        im.xz.cn.lingconsole.app.panel.model.AuthUser auth = AuthMiddleware.authUser(ctx);
        if (!im.xz.cn.lingconsole.app.panel.service.AccessFilter.hasAnyAppScope(auth)) {
            ctx.json(ok(List.of()));
            return;
        }
        Object result = proxy(ctx, "app:list", Map.of());
        if (result instanceof com.fasterxml.jackson.databind.JsonNode arr
                && arr.isArray()) {
            nodePermissionIndex.synchronizeSnapshot(ctx.pathParam("nodeId"), arr);
            java.util.List<Object> filtered = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode item : arr) {
                String appId = item.path("id").asText();
                if (nodePermissionIndex.ownsApp(ctx.pathParam("nodeId"), appId)
                        && auth.hasPermission("lingconsole.app.read." + appId)) {
                    filtered.add(sanitizeAppInfo(auth, item));
                }
            }
            result = filtered;
        }
        ctx.json(ok(result));
    }

    
    private void create(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, "lingconsole.app.write.*");
        User user = AuthMiddleware.currentUser(ctx);
        rejectAdvancedFields(ctx, true);
        CreateRequest req = ctx.bodyAsClass(CreateRequest.class);
        String nodeId = ctx.pathParam("nodeId");
        if (req.id() == null || req.id().isBlank()) {
            throw ApiException.badRequest("缺少应用 ID");
        }
        if (!nodePermissionIndex.reserveAppId(req.id(), nodeId)) {
            throw ApiException.badRequest("应用 ID 已被其他节点占用, 应用 ID 需全局唯一: " + req.id());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", req.id());
        payload.put("name", req.name());
        payload.put("command", req.command());
        payload.put("type", req.type() == null ? "general" : req.type());
        payload.put("autoStart", req.autoStart());
        payload.put("autoRestart", req.autoRestart());
        payload.put("maxRestartCount", req.maxRestartCount());
        payload.put("args", List.of());
        payload.put("environment", Map.of());
        Object result;
        boolean created = false;
        try {
            result = proxy(ctx, "app:create", payload);
            created = true;
            if (!nodePermissionIndex.activateAppId(req.id(), nodeId)) {
                throw ApiException.badRequest("应用已创建, 但全局应用 ID 注册激活失败; 已拒绝后续操作");
            }
        } finally {
            if (!created) {
                nodePermissionIndex.releaseReservation(req.id(), nodeId);
            }
        }
        nodePermissionIndex.refreshAppsAsync(ctx.pathParam("nodeId"));
        logService.record(user.getId(), "app.create", req.name(), "创建应用", ctx.ip());
        ctx.json(ok(respondAppInfo(ctx, result)));
    }

    
    private void get(Context ctx) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.read." + ctx.pathParam("appId"));
        Object result = proxy(ctx, "app:get", Map.of("id", ctx.pathParam("appId")));
        ctx.json(ok(respondAppInfo(ctx, result)));
    }

    
    static com.fasterxml.jackson.databind.JsonNode sanitizeAppInfo(
            im.xz.cn.lingconsole.app.panel.model.AuthUser auth,
            com.fasterxml.jackson.databind.JsonNode item) {
        if (item == null || !item.isObject()) {
            return item;
        }
        String appId = item.path("id").asText("");
        boolean privileged = auth.hasPermission("lingconsole.app.advanced." + appId);
        if (privileged) {
            return item;
        }
        var copy = ((com.fasterxml.jackson.databind.node.ObjectNode) item).deepCopy();
        copy.remove("command");
        copy.remove("args");
        copy.remove("environment");
        copy.remove("workDir");
        copy.remove("runAsUser");
        copy.remove("encoding");
        copy.remove("ptyType");
        copy.remove("protectAppFilesFromSymlinkEscape");
        return copy;
    }

    
    private Object respondAppInfo(Context ctx, Object result) {
        if (result instanceof com.fasterxml.jackson.databind.JsonNode n && n.isObject()) {
            im.xz.cn.lingconsole.app.panel.model.AuthUser auth = AuthMiddleware.authUser(ctx);
            if (auth != null) {
                return sanitizeAppInfo(auth, n);
            }
        }
        return result;
    }

    
    private void update(Context ctx) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.write." + ctx.pathParam("appId"));
        User user = AuthMiddleware.currentUser(ctx);
        rejectAdvancedFields(ctx, false);
        AppRequest req = ctx.bodyAsClass(AppRequest.class);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", ctx.pathParam("appId"));
        payload.put("name", req.name());
        payload.put("type", req.type());
        payload.put("autoStart", req.autoStart());
        payload.put("autoRestart", req.autoRestart());
        payload.put("maxRestartCount", req.maxRestartCount());
        Object result = proxy(ctx, "app:update", payload);
        nodePermissionIndex.refreshAppsAsync(ctx.pathParam("nodeId"));
        logService.record(user.getId(), "app.update", ctx.pathParam("appId"), "更新应用", ctx.ip());
        ctx.json(ok(respondAppInfo(ctx, result)));
    }

    
    private void delete(Context ctx) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.write." + ctx.pathParam("appId"));
        User user = AuthMiddleware.currentUser(ctx);
        Object result = proxy(ctx, "app:delete", Map.of("id", ctx.pathParam("appId")));
        nodePermissionIndex.releaseOwnedAppId(ctx.pathParam("appId"), ctx.pathParam("nodeId"));
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
                "lingconsole.app.write." + ctx.pathParam("appId"));
        User user = AuthMiddleware.currentUser(ctx);
        Object result = proxy(ctx, "app:" + action, Map.of("id", ctx.pathParam("appId")));
        logService.record(user.getId(), "app." + action, ctx.pathParam("appId"), "应用" + action, ctx.ip());
        ctx.json(ok(respondAppInfo(ctx, result)));
    }

    
    private void logs(Context ctx) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.read." + ctx.pathParam("appId"));
        int count = ctx.queryParamAsClass("count", Integer.class).getOrDefault(200);
        Object result = proxy(ctx, "app:log", Map.of("id", ctx.pathParam("appId"), "count", count));
        ctx.json(ok(result));
    }

    
    private void advancedGet(Context ctx) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.advanced." + ctx.pathParam("appId"));
        Object result = proxy(ctx, "app:get", Map.of("id", ctx.pathParam("appId")));
        ctx.json(ok(result));
    }

    
    private void advancedUpdate(Context ctx) {
        PermissionMiddleware.requirePermission(ctx,
                "lingconsole.app.advanced." + ctx.pathParam("appId"));
        User user = AuthMiddleware.currentUser(ctx);
        AdvancedRequest req = ctx.bodyAsClass(AdvancedRequest.class);
        if (Boolean.FALSE.equals(req.protectAppFilesFromSymlinkEscape())
                && !Boolean.TRUE.equals(req.confirmDisableAppFileSymlinkProtection())) {
            Object current = proxy(ctx, "app:get", Map.of("id", ctx.pathParam("appId")));
            if (current instanceof JsonNode n && n.path("protectAppFilesFromSymlinkEscape").asBoolean(true)) {
                throw ApiException.badRequest("关闭应用文件符号链接保护需要显式二次确认");
            }
        }
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
        if (req.protectAppFilesFromSymlinkEscape() != null) {
            payload.put("protectAppFilesFromSymlinkEscape", req.protectAppFilesFromSymlinkEscape());
        }
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
        String appId = ctx.pathParamMap().get("appId");
        if (appId != null && !appId.isBlank()) {
            nodePermissionIndex.requireOwnedApp(nodeId, appId);
        }
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

    private static void rejectAdvancedFields(Context ctx, boolean allowCommandForCreate) {
        try {
            JsonNode body = im.xz.cn.lingconsole.common.model.ApiResponse.mapper().readTree(ctx.body());
            if (body == null || !body.isObject()) {
                throw ApiException.badRequest("请求正文必须是 JSON 对象");
            }
            for (String field : ADVANCED_FIELDS) {
                if (!"command".equals(field) && body.has(field)) {
                    throw ApiException.forbidden("普通应用接口不允许高级字段: " + field);
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("请求正文格式不合法");
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
                             @JsonProperty("maxRestartCount") int maxRestartCount) {
    }

    public record CreateRequest(@JsonProperty("id") String id,
                                @JsonProperty("name") String name,
                                @JsonProperty("command") String command,
                                @JsonProperty("type") String type,
                                @JsonProperty("autoStart") boolean autoStart,
                                @JsonProperty("autoRestart") boolean autoRestart,
                                @JsonProperty("maxRestartCount") int maxRestartCount) {
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
                                  @JsonProperty("environment") Map<String, String> environment,
                                  @JsonProperty("protectAppFilesFromSymlinkEscape") Boolean protectAppFilesFromSymlinkEscape,
                                  @JsonProperty("confirmDisableAppFileSymlinkProtection") Boolean confirmDisableAppFileSymlinkProtection) {
    }
}
