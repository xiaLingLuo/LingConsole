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
package im.xz.cn.example.addon;

import im.xz.cn.lingconsole.addon.Addon;
import im.xz.cn.lingconsole.addon.AddonContext;
import im.xz.cn.lingconsole.addon.AddonRouteMethod;
import im.xz.cn.lingconsole.addon.AddonSocketHandler;
import im.xz.cn.lingconsole.addon.ConfigType;
import im.xz.cn.lingconsole.addon.service.AppService;
import im.xz.cn.lingconsole.addon.service.FileService;
import im.xz.cn.lingconsole.addon.service.NodeService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LingConsole 示例插件
 *
 * 演示:
 *  1. 生命周期 onLoad / onEnable / onDisable
 *  2. 标准配置 (STRING / TEXT / INT / BOOL / SELECT) 声明与读取
 *  3. Panel 动态路由 (GET / POST / PUT / DELETE)
 *  4. Daemon 动态路由
 *  5. Socket.IO 事件注册
 *  6. 定时任务调度
 *  7. 服务访问: 节点 / 应用 / 文件 / 监控 / 用户 / 日志 / 配置
 *  8. 日志、数据目录、私有数据目录
 */
public class ExampleAddon implements Addon {

    private static final String LIFECYCLE_FILE = "lifecycle.txt";

    private final AtomicInteger counter = new AtomicInteger();
    private Path dataDir;

    // 生命周期

    @Override
    public void onLoad(AddonContext ctx) {
        this.dataDir = ctx.addonDataDir();
        trace("load");

        ctx.logger().info("ExampleAddon onLoad: name={}, version={}, dataDir={}",
                ctx.info().name(), ctx.info().version(), ctx.addonDataDir());

        // 1) 声明标准配置
        defineConfig(ctx);

        // 2) Panel 动态路由
        registerPanelRoutes(ctx);

        // 3) Daemon 动态路由
        ctx.registerDaemonRoute(AddonRouteMethod.GET, "/ping",
                h -> h.json(Map.of("pong", true, "addon", ctx.info().name())));

        // 3.1) 控制台指令: exampleaddon:hello / exampleaddon:config
        ctx.registerCommand("hello", (command, args, sender) ->
                sender.sendMessage(ctx.config().getString("greeting", "Hello") + ", " + ctx.info().name()));
        ctx.registerCommand("config", (command, args, sender) ->
                sender.sendMessage("config: " + ctx.config().values()));

        // 3.2) 插件权限: 根为插件名 exampleaddon.manage
        ctx.registerPermission("manage", "示例插件管理权限");
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/protected",
                h -> h.json(Map.of("ok", true)), "manage");

        // 4) Socket.IO 事件
        ctx.registerSocketEvent("/panel", "addon:hello", (conn, event, data) ->
                conn.emit("addon:hello", Map.of("echo", data, "addon", ctx.info().name())));

        // 5) 定时任务
        ctx.scheduler().scheduleAtFixedRate(() ->
                ctx.logger().debug("ExampleAddon tick: counter={}", counter.get()),
                0, 30, TimeUnit.SECONDS);

        // 6) 侧栏菜单 (面板一体化)
        ctx.registerPanelMenu("示例插件", "/api/addon/exampleaddon/ui");

        // 7) 数据存储演示路由
        registerDataRoutes(ctx);

        // 8) 面板集成页面
        registerUiPage(ctx);

        // 9) 反向代理: 将 /api/addon/exampleaddon/pm/* 代理到本机 Daemon 的 /consoleapi
        //    (演示用; 真实场景如代理 phpMyAdmin 的 php-fpm 内部端口)
        ctx.registerPanelProxy("/pm", "http", "127.0.0.1", 55700, "");
    }

    @Override
    public void onEnable(AddonContext ctx) {
        trace("enable");
        ctx.logger().info("ExampleAddon enabled: greeting={}, mode={}, enabled={}",
                ctx.config().getString("greeting", "Hello"),
                ctx.config().getString("mode", "auto"),
                ctx.config().getBoolean("enabled", true));
    }

    @Override
    public void onDisable() {
        trace("disable");
    }

    // 配置声明
    private void defineConfig(AddonContext ctx) {
        ctx.config().define("greeting", ConfigType.STRING, "问候语", "/info 与 /hello 返回的问候内容", "Hello");
        ctx.config().define("banner", ConfigType.TEXT, "横幅", "/banner 返回的多行文本", "Welcome to LingConsole!\nPowered by exampleAddon.");
        ctx.config().define("maxCount", ConfigType.INT, "最大次数", "计数器上限", "5");
        ctx.config().define("enabled", ConfigType.BOOL, "启用", "是否启用 /hello 路由", "true");
        ctx.config().defineSelect("mode", "运行模式", "仅演示下拉配置", "auto", List.of("auto", "fast", "safe"));
    }

    // Panel 路由
    private void registerPanelRoutes(AddonContext ctx) {
        // GET /info: 插件信息 + 配置 + 目录 (演示 context 访问)
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/info", h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", ctx.info().name());
            m.put("version", ctx.info().version());
            m.put("author", ctx.info().author());
            m.put("dataDir", ctx.dataDir().toString());
            m.put("addonDataDir", ctx.addonDataDir().toString());
            m.put("config", ctx.config().values());
            m.put("counter", counter.get());
            h.json(m);
        }, AddonContext.PUBLIC);

        // GET /hello: 返回配置的问候语 (演示配置读取 + 计数器)
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/hello", h -> {
            if (!ctx.config().getBoolean("enabled", true)) {
                h.status(403);
                h.result("disabled");
                return;
            }
            int c = counter.incrementAndGet();
            h.json(Map.of(
                    "greeting", ctx.config().getString("greeting", "Hello"),
                    "count", c,
                    "maxCount", ctx.config().getInt("maxCount", 5)));
        }, AddonContext.PUBLIC);

        // POST /echo: 回显请求体 (演示 POST + body 读取)
        ctx.registerPanelRoute(AddonRouteMethod.POST, "/echo", h ->
                h.json(Map.of("received", h.bodyAsClass(Map.class))), AddonContext.PUBLIC);

        // PUT /reset: 重置计数器 (演示 PUT)
        ctx.registerPanelRoute(AddonRouteMethod.PUT, "/reset", h -> {
            counter.set(0);
            h.json(Map.of("reset", true));
        }, AddonContext.PUBLIC);

        // DELETE /clear-data: 清空 lifecycle 文件 (演示 DELETE + addonDataDir)
        ctx.registerPanelRoute(AddonRouteMethod.DELETE, "/clear-data", h -> {
            try {
                Files.deleteIfExists(dataDir.resolve(LIFECYCLE_FILE));
                h.json(Map.of("cleared", true));
            } catch (Exception e) {
                h.status(500);
                h.result(String.valueOf(e.getMessage()));
            }
        }, AddonContext.PUBLIC);

        // GET /banner: 多行文本配置 (演示 TEXT)
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/banner",
                h -> h.result(ctx.config().getString("banner", "")), AddonContext.PUBLIC);

        // GET /services/nodes: 服务访问 — 节点列表
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/services/nodes", h -> {
            try {
                h.json(Map.of("nodes", ctx.nodes().listNodes()));
            } catch (Exception e) {
                h.json(Map.of("error", String.valueOf(e.getMessage())));
            }
        });

        // GET /services/apps?nodeId=xxx: 服务访问 — 应用列表
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/services/apps", h -> {
            String nodeId = h.queryParam("nodeId");
            try {
                h.json(Map.of("apps", ctx.apps().listApps(nodeId == null ? "" : nodeId)));
            } catch (Exception e) {
                h.json(Map.of("error", String.valueOf(e.getMessage())));
            }
        });

        // GET /services/files?nodeId=xxx&path=/...: 服务访问 — 文件列表
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/services/files", h -> {
            String nodeId = h.queryParam("nodeId");
            String path = h.queryParam("path");
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            try {
                h.json(Map.of("files", ctx.files().listFiles(nodeId == null ? "" : nodeId, path)));
            } catch (Exception e) {
                h.json(Map.of("error", String.valueOf(e.getMessage())));
            }
        });

        // GET /services/monitor?nodeId=xxx: 服务访问 — 监控
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/services/monitor", h -> {
            String nodeId = h.queryParam("nodeId");
            try {
                h.json(ctx.monitor().snapshot(nodeId == null ? "" : nodeId));
            } catch (Exception e) {
                h.json(Map.of("error", String.valueOf(e.getMessage())));
            }
        });

        // GET /services/users: 服务访问 — 用户列表
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/services/users", h -> {
            try {
                h.json(Map.of("users", ctx.users().listUsers()));
            } catch (Exception e) {
                h.json(Map.of("error", String.valueOf(e.getMessage())));
            }
        });

        // POST /services/log: 服务访问 — 写入操作日志
        ctx.registerPanelRoute(AddonRouteMethod.POST, "/services/log", h -> {
            Map body = h.bodyAsClass(Map.class);
            ctx.logs().record("exampleAddon", String.valueOf(body.get("target")),
                    String.valueOf(body.get("detail")));
            h.json(Map.of("recorded", true));
        });

        // GET /services/exec?nodeId=xxx&cmd=...: 服务访问 — 命令执行
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/services/exec", h -> {
            String nodeId = h.queryParam("nodeId");
            String cmd = h.queryParam("cmd");
            try {
                im.xz.cn.lingconsole.addon.ExecResult r = ctx.exec()
                        .exec(nodeId == null ? "" : nodeId, cmd == null ? "echo no-cmd" : cmd, 10000);
                h.json(Map.of("exitCode", r.exitCode(), "stdout", r.stdout(), "stderr", r.stderr()));
            } catch (Exception e) {
                h.json(Map.of("error", String.valueOf(e.getMessage())));
            }
        });

        // GET /services/apps/signal?nodeId=xxx&appId=yyy&signal=SIGTERM: 服务访问 — 进程信号
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/services/apps/signal", h -> {
            String nodeId = h.queryParam("nodeId");
            String appId = h.queryParam("appId");
            String signal = h.queryParam("signal");
            if (signal == null || signal.isEmpty()) {
                signal = "SIGTERM";
            }
            try {
                boolean ok = ctx.apps().signalApp(nodeId == null ? "" : nodeId,
                        appId == null ? "" : appId, signal);
                h.json(Map.of("ok", ok, "signal", signal));
            } catch (Exception e) {
                h.json(Map.of("error", String.valueOf(e.getMessage())));
            }
        });

        // POST /services/apps/create: 服务访问 — 创建应用 {nodeId,name,command,args,workDir}
        ctx.registerPanelRoute(AddonRouteMethod.POST, "/services/apps/create", h -> {
            Map body = h.bodyAsClass(Map.class);
            try {
                String nodeId = String.valueOf(body.getOrDefault("nodeId", ""));
                String name = String.valueOf(body.getOrDefault("name", ""));
                String command = String.valueOf(body.getOrDefault("command", ""));
                Object argsObj = body.get("args");
                List<String> args = new ArrayList<>();
                if (argsObj instanceof List<?> list) {
                    list.forEach(o -> args.add(String.valueOf(o)));
                }
                String workDir = body.get("workDir") == null ? null : String.valueOf(body.get("workDir"));
                Map created = ctx.apps().createApp(nodeId, name, command, args, workDir);
                h.json(created == null ? Map.of("created", false) : created);
            } catch (Exception e) {
                h.json(Map.of("error", String.valueOf(e.getMessage())));
            }
        });
    }

    // 数据存储 (ctx.data()) 演示

    private void registerDataRoutes(AddonContext ctx) {
        // GET /data?set=key:value 设置一个键值, ?get=key 读取, ?del=key 删除, 无参列出全部
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/data", h -> {
            String set = h.queryParam("set");
            String get = h.queryParam("get");
            String del = h.queryParam("del");
            if (set != null && set.contains(":")) {
                int i = set.indexOf(':');
                ctx.data().put(set.substring(0, i), set.substring(i + 1));
                h.json(Map.of("set", set));
            } else if (get != null) {
                h.json(Map.of(get, ctx.data().get(get)));
            } else if (del != null) {
                ctx.data().delete(del);
                h.json(Map.of("deleted", del));
            } else {
                h.json(Map.of("data", ctx.data().all()));
            }
        }, AddonContext.PUBLIC);
    }

    // 面板集成页面 (/ui) 演示

    private void registerUiPage(AddonContext ctx) {
        // 返回一个完整 HTML 页面, 由侧栏「示例插件」菜单进入
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/ui", h -> {
            String html = """
                    <!DOCTYPE html>
                    <html lang="zh-CN">
                    <head><meta charset="UTF-8"><title>示例插件 - LingConsole</title>
                    <style>
                      body{font-family:system-ui,sans-serif;background:#F5F7FA;margin:0;padding:40px}
                      .card{background:#fff;border-radius:8px;padding:24px;max-width:640px;margin:0 auto;box-shadow:0 2px 8px rgba(0,0,0,.08)}
                      h1{font-size:20px;margin:0 0 16px}
                      table{width:100%;border-collapse:collapse;margin-top:12px}
                      td,th{border:1px solid #E8EBF0;padding:8px 10px;text-align:left;font-size:13px}
                      .btn{display:inline-block;margin-top:16px;padding:8px 20px;border-radius:6px;background:#66CCFF;color:#1A1A2E;text-decoration:none;font-size:14px}
                    </style></head>
                    <body><div class="card">
                    <h1>示例插件 - 面板集成页面</h1>
                    <p>此页面由插件路由 /api/addon/exampleaddon/ui 渲染, 通过侧栏「示例插件」菜单进入。</p>
                    <table><tr><th>插件</th><th>版本</th><th>作者</th></tr>
                    <tr><td>exampleaddon</td><td>1.1.86</td><td>xiaLingLuo</td></tr></table>
                    <table><tr><th>配置键</th><th>当前值</th></tr>
                    <tr><td>greeting</td><td>__GREETING__</td></tr>
                    <tr><td>mode</td><td>__MODE__</td></tr>
                    <tr><td>enabled</td><td>__ENABLED__</td></tr></table>
                    <a class="btn" href="/addons">返回插件管理</a>
                    </div></body></html>
                    """;
            html = html.replace("__GREETING__", escapeHtml(ctx.config().getString("greeting", "Hello")))
                    .replace("__MODE__", escapeHtml(ctx.config().getString("mode", "auto")))
                    .replace("__ENABLED__", String.valueOf(ctx.config().getBoolean("enabled", true)));
            h.contentType("text/html; charset=utf-8");
            h.result(html);
        }, AddonContext.PUBLIC);
    }

    private static String escapeHtml(String s) {
        return String.valueOf(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // 工具

    private void trace(String event) {
        try {
            if (dataDir != null) {
                Files.createDirectories(dataDir);
                Files.writeString(dataDir.resolve(LIFECYCLE_FILE), event + "\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception ignored) {

        }
    }
}
