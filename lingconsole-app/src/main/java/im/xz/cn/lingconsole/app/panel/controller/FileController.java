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
import im.xz.cn.lingconsole.app.panel.remote.DaemonHttpProxy;
import im.xz.cn.lingconsole.app.panel.service.LogService;
import im.xz.cn.lingconsole.app.panel.service.NodeService;
import im.xz.cn.lingconsole.common.util.ErrorMessageUtil;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;


public class FileController {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final NodeService nodeService;
    private final DaemonHttpProxy proxy;
    private final LogService logService;

    public FileController(NodeService nodeService, DaemonHttpProxy proxy, LogService logService) {
        this.nodeService = nodeService;
        this.proxy = proxy;
        this.logService = logService;
    }

    public void register(RoutesConfig routes, String prefix) {
        
        routes.get(prefix + "/nodes/{nodeId}/files/list", this::list);
        routes.get(prefix + "/nodes/{nodeId}/files/read", this::read);
        routes.post(prefix + "/nodes/{nodeId}/files/write", this::write);
        routes.post(prefix + "/nodes/{nodeId}/files/mkdir", this::mkdir);
        routes.delete(prefix + "/nodes/{nodeId}/files", this::delete);
        routes.post(prefix + "/nodes/{nodeId}/files/upload", this::upload);
        routes.get(prefix + "/nodes/{nodeId}/files/download", this::download);
        routes.post(prefix + "/nodes/{nodeId}/files/rename", this::rename);
        routes.post(prefix + "/nodes/{nodeId}/files/copy", this::copy);
        routes.post(prefix + "/nodes/{nodeId}/files/7zip/status", this::zipStatus);
        routes.post(prefix + "/nodes/{nodeId}/files/7zip/install", this::zipInstall);
        routes.post(prefix + "/nodes/{nodeId}/files/archive/compress", this::zipCompress);
        routes.post(prefix + "/nodes/{nodeId}/files/archive/extract", this::zipExtract);
        routes.get(prefix + "/nodes/{nodeId}/files/drives", this::drives);

        
        routes.get(prefix + "/nodes/{nodeId}/apps/{appId}/files/list", this::list);
        routes.get(prefix + "/nodes/{nodeId}/apps/{appId}/files/read", this::read);
        routes.post(prefix + "/nodes/{nodeId}/apps/{appId}/files/write", this::write);
        routes.post(prefix + "/nodes/{nodeId}/apps/{appId}/files/mkdir", this::mkdir);
        routes.delete(prefix + "/nodes/{nodeId}/apps/{appId}/files", this::delete);
        routes.post(prefix + "/nodes/{nodeId}/apps/{appId}/files/upload", this::upload);
        routes.get(prefix + "/nodes/{nodeId}/apps/{appId}/files/download", this::download);
        routes.post(prefix + "/nodes/{nodeId}/apps/{appId}/files/rename", this::rename);
        routes.post(prefix + "/nodes/{nodeId}/apps/{appId}/files/copy", this::copy);
        routes.post(prefix + "/nodes/{nodeId}/apps/{appId}/files/archive/compress", this::zipCompress);
        routes.post(prefix + "/nodes/{nodeId}/apps/{appId}/files/archive/extract", this::zipExtract);
    }

    
    private void list(Context ctx) {
        requireFilePerm(ctx, false);
        forward(ctx, call(() -> proxy.get(node(ctx), fileApi(ctx, "/list"), Map.of("path", requirePath(ctx)))));
    }

    
    private void read(Context ctx) {
        requireFilePerm(ctx, false);
        forward(ctx, call(() -> proxy.get(node(ctx), fileApi(ctx, "/read"), Map.of("path", requirePath(ctx)))));
    }

    
    private void write(Context ctx) {
        requireFilePerm(ctx, true);
        User user = AuthMiddleware.currentUser(ctx);
        WriteRequest req = ctx.bodyAsClass(WriteRequest.class);
        forward(ctx, call(() -> proxy.postJson(node(ctx), fileApi(ctx, "/write"), null,
                "{\"path\":\"" + escapeJson(req.path()) + "\",\"content\":\"" + escapeJson(req.content()) + "\"}")));
        logService.record(user.getId(), "file.write", req.path(), "写入文件", ctx.ip());
    }

    
    private void mkdir(Context ctx) {
        requireFilePerm(ctx, true);
        User user = AuthMiddleware.currentUser(ctx);
        forward(ctx, call(() -> proxy.postJson(node(ctx), fileApi(ctx, "/mkdir"), Map.of("path", requirePath(ctx)), null)));
        logService.record(user.getId(), "file.mkdir", ctx.queryParam("path"), "创建目录", ctx.ip());
    }

    
    private void delete(Context ctx) {
        requireFilePerm(ctx, true);
        User user = AuthMiddleware.currentUser(ctx);
        forward(ctx, call(() -> proxy.delete(node(ctx), fileApi(ctx, ""), Map.of("path", requirePath(ctx)))));
        logService.record(user.getId(), "file.delete", ctx.queryParam("path"), "删除文件", ctx.ip());
    }

    
    private void upload(Context ctx) {
        requireFilePerm(ctx, true);
        User user = AuthMiddleware.currentUser(ctx);
        String path = requirePath(ctx);
        var uploaded = ctx.uploadedFiles("file");
        if (uploaded.isEmpty()) {
            throw ApiException.badRequest("缺少文件");
        }
        var file = uploaded.getFirst();
        try {
            forward(ctx, call(() -> proxy.uploadRaw(node(ctx), fileApi(ctx, "/upload-raw"), path, file.filename(), file.content())));
            logService.record(user.getId(), "file.upload", path + "/" + file.filename(), "上传文件", ctx.ip());
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorMessageUtil.with("上传失败", e));
        }
    }

    
    private void download(Context ctx) {
        requireFilePerm(ctx, false);
        Node node = node(ctx);
        String path = requirePath(ctx);
        try {
            HttpResponse<java.io.InputStream> resp = call(() -> proxy.download(node, fileApi(ctx, "/download"), path));
            try (java.io.InputStream body = resp.body()) {
                if (resp.statusCode() != 200) {
                    ctx.status(resp.statusCode());
                    ctx.json(body.readAllBytes());
                    return;
                }
                String fileName = im.xz.cn.lingconsole.common.util.PathUtil.safeFileName(
                        Path.of(path));
                ctx.contentType("application/octet-stream");
                ctx.header("Content-Disposition",
                        "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''"
                                + java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
                ctx.result(body.readAllBytes());
            }
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorMessageUtil.with("下载失败", e));
        }
    }

    
    private void rename(Context ctx) {
        requireFilePerm(ctx, true);
        User user = AuthMiddleware.currentUser(ctx);
        String path = requirePath(ctx);
        String newName = ctx.queryParam("newName");
        if (newName == null || newName.isBlank()) {
            throw ApiException.badRequest("缺少 newName 参数");
        }
        forward(ctx, call(() -> proxy.postJson(node(ctx), fileApi(ctx, "/rename"),
                Map.of("path", path, "newName", newName), null)));
        logService.record(user.getId(), "file.rename", path, "重命名为 " + newName, ctx.ip());
    }

    
    private void copy(Context ctx) {
        requireFilePerm(ctx, true);
        User user = AuthMiddleware.currentUser(ctx);
        String path = requirePath(ctx);
        String dest = ctx.queryParam("dest");
        if (dest == null || dest.isBlank()) {
            throw ApiException.badRequest("缺少 dest 参数");
        }
        forward(ctx, call(() -> proxy.postJson(node(ctx), fileApi(ctx, "/copy"),
                Map.of("path", path, "dest", dest), null)));
        logService.record(user.getId(), "file.copy", path, "复制到 " + dest, ctx.ip());
    }

    
    private void zipStatus(Context ctx) {
        requireFilePerm(ctx, false);
        forward(ctx, call(() -> proxy.postJson(node(ctx), "/files/7zip/status", null, "{}")));
    }

    
    private void zipInstall(Context ctx) {
        requireFilePerm(ctx, true);
        User user = AuthMiddleware.currentUser(ctx);
        forward(ctx, call(() -> proxy.postJson(node(ctx), "/files/7zip/install", null, "{}")));
        logService.record(user.getId(), "file.7zipInstall", node(ctx).getName(), "自动安装 7zip", ctx.ip());
    }

    
    private void zipCompress(Context ctx) {
        requireFilePerm(ctx, true);
        User user = AuthMiddleware.currentUser(ctx);
        ZipOpRequest req = ctx.bodyAsClass(ZipOpRequest.class);
        try {
            String body = MAPPER.writeValueAsString(req);
            forward(ctx, call(() -> proxy.postJson(node(ctx), fileApi(ctx, "/archive/compress"), null, body)));
            logService.record(user.getId(), "file.zipCompress", req.archive(), "压缩文件", ctx.ip());
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorMessageUtil.with("压缩失败", e));
        }
    }

    
    private void zipExtract(Context ctx) {
        requireFilePerm(ctx, true);
        User user = AuthMiddleware.currentUser(ctx);
        ZipOpRequest req = ctx.bodyAsClass(ZipOpRequest.class);
        try {
            String body = MAPPER.writeValueAsString(req);
            forward(ctx, call(() -> proxy.postJson(node(ctx), fileApi(ctx, "/archive/extract"), null, body)));
            logService.record(user.getId(), "file.zipExtract", req.archive(), "解压文件", ctx.ip());
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorMessageUtil.with("解压失败", e));
        }
    }

    
    private void drives(Context ctx) {
        requireFilePerm(ctx, false);
        forward(ctx, call(() -> proxy.get(node(ctx), "/files/drives", null)));
    }

    
    
    

    
    private void requireFilePerm(Context ctx, boolean write) {
        String nodeId = ctx.pathParamMap().get("nodeId");
        String appId = ctx.pathParamMap().get("appId");
        if (appId != null && !appId.isBlank()) {
            PermissionMiddleware.requirePermission(ctx,
                    "lingconsole.file.app." + nodeId + "." + appId);
        } else {
            PermissionMiddleware.requirePermission(ctx, "lingconsole.file.node." + nodeId);
        }
    }

    
    private String fileApi(Context ctx, String sub) {
        String appId = ctx.pathParamMap().get("appId");
        if (appId != null && !appId.isBlank()) {
            return "/apps/" + appId + "/files" + sub;
        }
        return "/files" + sub;
    }

    private Node node(Context ctx) {
        Node node = nodeService.findById(ctx.pathParam("nodeId")).orElse(null);
        if (node == null) {
            throw ApiException.notFound("节点不存在");
        }
        return node;
    }

    private String requirePath(Context ctx) {
        String path = ctx.queryParam("path");
        if (path == null) {
            throw ApiException.badRequest("缺少 path 参数");
        }
        
        return path;
    }

    private void forward(Context ctx, HttpResponse<String> resp) {
        ctx.status(resp.statusCode());
        ctx.contentType("application/json; charset=utf-8");
        ctx.result(resp.body());
    }

    private <T> HttpResponse<T> call(ProxyCall<T> call) {
        try {
            return call.run();
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorMessageUtil.with("节点请求失败", e));
        }
    }

    @FunctionalInterface
    private interface ProxyCall<T> {
        HttpResponse<T> run() throws Exception;
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public record WriteRequest(@JsonProperty("path") String path,
                               @JsonProperty("content") String content) {
    }

    public record ZipOpRequest(@JsonProperty("files") java.util.List<String> files,
                               @JsonProperty("archive") String archive,
                               @JsonProperty("dest") String dest) {
    }
}
