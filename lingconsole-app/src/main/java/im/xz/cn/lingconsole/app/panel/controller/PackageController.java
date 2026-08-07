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
import com.fasterxml.jackson.databind.ObjectMapper;
import im.xz.cn.lingconsole.app.panel.exception.ApiException;
import im.xz.cn.lingconsole.app.panel.middleware.AuthMiddleware;
import im.xz.cn.lingconsole.app.panel.middleware.PermissionMiddleware;
import im.xz.cn.lingconsole.app.panel.model.Node;
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.app.panel.remote.DaemonHttpProxy;
import im.xz.cn.lingconsole.app.panel.service.LogService;
import im.xz.cn.lingconsole.app.panel.service.NodeService;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import im.xz.cn.lingconsole.common.permission.Permissions;
import im.xz.cn.lingconsole.common.util.ErrorMessageUtil;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PackageController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> DEBIAN_IDS = Set.of(
            "debian", "ubuntu", "linuxmint", "kali", "raspbian", "elementary",
            "pop", "zorin", "deepin", "uos", "neon", "devuan", "parrot");

    private final NodeService nodeService;
    private final DaemonHttpProxy proxy;
    private final LogService logService;

    public PackageController(NodeService nodeService, DaemonHttpProxy proxy, LogService logService) {
        this.nodeService = nodeService;
        this.proxy = proxy;
        this.logService = logService;
    }

    private static final java.util.regex.Pattern PACKAGE_NAME_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9+._:^-]*");

    public void register(RoutesConfig routes, String prefix) {
        routes.get(prefix + "/nodes/{nodeId}/packages/status", this::status);
        routes.get(prefix + "/nodes/{nodeId}/packages", this::listInstalled);
        routes.post(prefix + "/nodes/{nodeId}/packages/update", this::updateSources);
        routes.post(prefix + "/nodes/{nodeId}/packages/upgrade", this::upgrade);
        routes.post(prefix + "/nodes/{nodeId}/packages/full-upgrade", this::fullUpgrade);
        routes.post(prefix + "/nodes/{nodeId}/packages/autoremove", this::autoremove);
        routes.post(prefix + "/nodes/{nodeId}/packages/remove", this::remove);
        routes.post(prefix + "/nodes/{nodeId}/packages/search", this::search);
        routes.post(prefix + "/nodes/{nodeId}/packages/install", this::install);
    }

    
    private static String requirePackageName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("缺少软件包名");
        }
        String n = name.trim();
        if (!PACKAGE_NAME_PATTERN.matcher(n).matches()) {
            throw ApiException.badRequest("软件包名含非法字符: " + n);
        }
        return n;
    }

    private void require(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.PACKAGES);
    }

    private Node node(Context ctx) {
        Node n = nodeService.findById(ctx.pathParam("nodeId")).orElse(null);
        if (n == null) {
            throw ApiException.notFound("节点不存在");
        }
        return n;
    }

    
    private void status(Context ctx) {
        require(ctx);
        Node node = node(ctx);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            ExecResult r = run(node, "cat /etc/os-release 2>/dev/null", 8000);
            String osId = "";
            String pretty = "";
            if (r.exitCode == 0 && r.stdout != null) {
                for (String line : r.stdout.split("\n")) {
                    if (line.startsWith("ID=")) osId = line.substring(3).trim().replace("\"", "");
                    if (line.startsWith("PRETTY_NAME=")) pretty = line.substring(12).trim().replace("\"", "");
                }
            }
            boolean supported = DEBIAN_IDS.contains(osId.toLowerCase()) && r.exitCode == 0;
            result.put("supported", supported);
            result.put("osId", osId);
            result.put("osName", pretty.isBlank() ? osId : pretty);
        } catch (Exception e) {
            result.put("supported", false);
            result.put("osName", ErrorMessageUtil.friendly(e));
        }
        ctx.json(ApiResponse.ok(result));
    }

    
    private void listInstalled(Context ctx) {
        require(ctx);
        Node node = node(ctx);
        ExecResult r = run(node,
                "dpkg-query -W -f='${Package}\\t${Version}\\t${Status}\\n' 2>/dev/null", 30000);
        List<Map<String, Object>> packages = new ArrayList<>();
        if (r.exitCode == 0 && r.stdout != null) {
            for (String line : r.stdout.split("\n")) {
                String[] f = line.split("\\t");
                if (f.length >= 3 && f[2].contains("installed")) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", f[0].trim());
                    m.put("version", f[1].trim());
                    packages.add(m);
                }
            }
        }
        ctx.json(ApiResponse.ok(Map.of("packages", packages)));
    }

    private void updateSources(Context ctx) {
        runOp(ctx, "更新软件源", "apt-get update -o DPkg::Lock::Timeout=60 2>&1 | tail -n 40", 120000, "packages.update");
    }

    private void upgrade(Context ctx) {
        runOp(ctx, "升级软件包", "apt-get -y upgrade -o DPkg::Lock::Timeout=60 2>&1 | tail -n 60", 120000, "packages.upgrade");
    }

    private void fullUpgrade(Context ctx) {
        runOp(ctx, "智能升级", "apt-get -y full-upgrade -o DPkg::Lock::Timeout=60 2>&1 | tail -n 60", 120000, "packages.fullUpgrade");
    }

    private void autoremove(Context ctx) {
        runOp(ctx, "清理无用依赖", "apt-get -y autoremove -o DPkg::Lock::Timeout=60 2>&1 | tail -n 40", 120000, "packages.autoremove");
    }

    private void runOp(Context ctx, String label, String command, int timeoutMs, String logKey) {
        require(ctx);
        User user = AuthMiddleware.currentUser(ctx);
        Node node = node(ctx);
        ExecResult r = run(node, command, timeoutMs);
        logService.record(user.getId(), logKey, node.getName(), label, ctx.ip());
        ctx.json(ApiResponse.ok(Map.of(
                "exitCode", r.exitCode,
                "output", r.output(),
                "timedOut", r.timedOut)));
    }

    
    private void remove(Context ctx) {
        require(ctx);
        User user = AuthMiddleware.currentUser(ctx);
        Node node = node(ctx);
        String name = requirePackageName(ctx.bodyAsClass(NameRequest.class).name());
        ExecResult r = run(node, "apt-get -y remove " + name + " 2>&1 | tail -n 60", 120000);
        logService.record(user.getId(), "packages.remove", name, "卸载软件包", ctx.ip());
        ctx.json(ApiResponse.ok(Map.of(
                "exitCode", r.exitCode,
                "output", r.output(),
                "timedOut", r.timedOut)));
    }

    
    private void search(Context ctx) {
        require(ctx);
        Node node = node(ctx);
        String name = requirePackageName(ctx.bodyAsClass(NameRequest.class).name());
        ExecResult r = run(node, "apt-cache search " + name + " 2>/dev/null", 30000);
        List<Map<String, String>> packages = new ArrayList<>();
        if (r.exitCode == 0 && r.stdout != null) {
            for (String line : r.stdout.split("\n")) {
                int sep = line.indexOf(" - ");
                if (sep > 0) {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("name", line.substring(0, sep).trim());
                    m.put("description", line.substring(sep + 3).trim());
                    packages.add(m);
                }
            }
        }
        ctx.json(ApiResponse.ok(Map.of("packages", packages)));
    }

    
    private void install(Context ctx) {
        require(ctx);
        User user = AuthMiddleware.currentUser(ctx);
        Node node = node(ctx);
        String name = requirePackageName(ctx.bodyAsClass(NameRequest.class).name());
        ExecResult r = run(node, "apt-get -y install " + name + " -o DPkg::Lock::Timeout=60 2>&1 | tail -n 80", 120000);
        logService.record(user.getId(), "packages.install", name, "安装软件包", ctx.ip());
        ctx.json(ApiResponse.ok(Map.of(
                "exitCode", r.exitCode,
                "output", r.output(),
                "timedOut", r.timedOut)));
    }

    
    private ExecResult run(Node node, String command, int timeoutMs) {
        try {
            String body = "{\"command\":" + MAPPER.writeValueAsString(command) + ",\"timeoutMs\":" + timeoutMs + "}";
            HttpResponse<String> resp = proxy.postJson(node, "/exec", null, body);
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode data = root.path("data");
            return new ExecResult(
                    data.path("exitCode").asInt(-1),
                    data.path("stdout").asText(),
                    data.path("stderr").asText(),
                    data.path("timedOut").asBoolean(false));
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorMessageUtil.with("执行命令失败", e));
        }
    }

    private record ExecResult(int exitCode, String stdout, String stderr, boolean timedOut) {
        String output() {
            StringBuilder sb = new StringBuilder();
            if (stdout != null && !stdout.isBlank()) sb.append(stdout);
            if (stderr != null && !stderr.isBlank()) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(stderr);
            }
            if (timedOut) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append("[执行超时]");
            }
            return sb.toString();
        }
    }

    public record NameRequest(@JsonProperty("name") String name) {
    }
}
