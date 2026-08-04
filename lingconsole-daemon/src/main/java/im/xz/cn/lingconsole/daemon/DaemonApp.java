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
package im.xz.cn.lingconsole.daemon;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import im.xz.cn.lingconsole.common.config.Constants;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import im.xz.cn.lingconsole.common.model.SystemInfo;
import im.xz.cn.lingconsole.common.socketio.SocketIOConnection;
import im.xz.cn.lingconsole.common.socketio.SocketIOResponse;
import im.xz.cn.lingconsole.common.socketio.SocketIOServer;
import im.xz.cn.lingconsole.common.util.ErrorMessageUtil;
import im.xz.cn.lingconsole.daemon.model.AppInfo;
import im.xz.cn.lingconsole.daemon.service.AppManager;
import im.xz.cn.lingconsole.daemon.service.AppProcess;
import im.xz.cn.lingconsole.daemon.service.FileSystemService;
import im.xz.cn.lingconsole.daemon.service.MonitorService;
import im.xz.cn.lingconsole.daemon.service.PassportManager;
import im.xz.cn.lingconsole.daemon.service.TerminalService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class DaemonApp {

    private static final Logger log = LoggerFactory.getLogger(DaemonApp.class);

    private static final java.util.Set<String> ALLOWED_SIGNALS = java.util.Set.of(
            "SIGTERM", "SIGKILL", "SIGHUP", "SIGUSR1", "SIGUSR2", "SIGINT", "SIGQUIT");
    private static final java.util.concurrent.Semaphore EXEC_SEMAPHORE =
            new java.util.concurrent.Semaphore(4);

    private static final String AUTO_INSTALL_7ZIP = """
            set +e
            if command -v 7z >/dev/null 2>&1 || command -v 7zz >/dev/null 2>&1; then echo "7zip already installed"; exit 0; fi
            . /etc/os-release 2>/dev/null
            MAJOR=$(echo "$VERSION_ID" | cut -d. -f1)
            apt-get update -o DPkg::Lock::Timeout=60 >/dev/null 2>&1
            if [ -n "$MAJOR" ] && [ "$MAJOR" -lt 12 ] 2>/dev/null; then
              apt-get -y install p7zip-full -o DPkg::Lock::Timeout=60 2>&1 | tail -n 40
            else
              apt-get -y install 7zip -o DPkg::Lock::Timeout=60 2>&1 | tail -n 40
            fi
            if command -v 7z >/dev/null 2>&1 || command -v 7zz >/dev/null 2>&1; then echo "7zip installed via APT"; exit 0; fi
            echo "APT 安装失败, 尝试官方二进制..."
            apt-get -y install ca-certificates curl wget xz-utils -o DPkg::Lock::Timeout=60 >/dev/null 2>&1
            ARCH=$(dpkg --print-architecture 2>/dev/null)
            case "$ARCH" in
              amd64) TAG=linux-x64 ;;
              arm64) TAG=linux-arm64 ;;
              armhf) TAG=linux-arm ;;
              i386) TAG=linux-x86 ;;
              *) echo "不支持的架构: $ARCH"; exit 1 ;;
            esac
            release_json=$(curl -fsSL --max-time 30 https://api.github.com/repos/ip7z/7zip/releases/latest 2>/dev/null)
            download_url=$(printf '%s
            ' "$release_json" | awk -F'"' -v a="$TAG" '/browser_download_url/ && $0 ~ a && /tar\\.xz/ {print $4; exit}')
            if [ -z "$download_url" ]; then echo "无法获取 7zip 下载地址"; exit 1; fi
            archive_name=${download_url##*/}
            cd /tmp
            wget -q -O "$archive_name" "$download_url" || { echo "下载失败"; exit 1; }
            tar xf "$archive_name"
            mv -f 7zz /usr/local/bin/ 2>/dev/null || mv -f 7zz ~/.local/bin/
            if command -v 7z >/dev/null 2>&1 || command -v 7zz >/dev/null 2>&1; then echo "7zip installed via binary"; else echo "7zip 安装失败"; exit 1; fi
            """;

    private static final String ROOT_PAGE = loadRootPage();

    private final DaemonConfig config;
    private final AuthManager authManager;
    private final im.xz.cn.lingconsole.common.addon.AddonManager addonManager;
    
    private final im.xz.cn.lingconsole.common.addon.AddonSocketRegistry addonSocketRegistry;

    private Javalin javalin;
    private SocketIOServer socketIOServer;
    private AppManager appManager;
    private TerminalService terminalService;
    private PassportManager passportManager;
    private FileSystemService fileSystemService;
    private MonitorService monitorService;

    
    private final Set<String> authenticatedSessions = ConcurrentHashMap.newKeySet();

    
    private final Map<String, TerminalService.TerminalSession> streamBindings = new ConcurrentHashMap<>();

    private final java.util.concurrent.ScheduledExecutorService terminalCleanupExecutor =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "terminal-cleanup");
                t.setDaemon(true);
                return t;
            });

    public DaemonApp(DaemonConfig config) {
        this(config, null, null);
    }

    public DaemonApp(DaemonConfig config,
                     im.xz.cn.lingconsole.common.addon.AddonManager addonManager,
                     im.xz.cn.lingconsole.common.addon.AddonSocketRegistry addonSocketRegistry) {
        this.config = config;
        this.authManager = new AuthManager(config.key());
        this.addonManager = addonManager;
        this.addonSocketRegistry = addonSocketRegistry;
    }

    public AuthManager authManager() {
        return authManager;
    }

    private static String loadRootPage() {
        String fallback = "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>LingConsole 守护程序</title></head>"
                + "<body style=\"font-family:sans-serif;text-align:center;padding-top:60px\">"
                + "<h1>LingConsole 守护程序运行中</h1>"
                + "<p>此端口上正在运行 LingConsole 的 守护程序，正常情况下，你不应该直接访问！</p></body></html>";
        try (InputStream in = DaemonApp.class.getResourceAsStream("/deamon.html")) {
            if (in == null) {
                log.warn("未找到根页面资源 /deamon.html, 使用内置回退页");
                return fallback;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("加载根页面失败: {}", e.getMessage());
            return fallback;
        }
    }

    public im.xz.cn.lingconsole.common.socketio.SocketIOServer socketIOServer() {
        return socketIOServer;
    }

    public void start() {
        socketIOServer = new SocketIOServer();
        appManager = new AppManager(config.defaultAppPath());
        terminalService = new TerminalService(config.shellMode());
        passportManager = new PassportManager();
        fileSystemService = new FileSystemService();
        monitorService = new MonitorService();
        socketIOServer.onDisconnect(sid -> {
            TerminalService.TerminalSession session = streamBindings.remove(sid);
            if (session != null) {
                terminalService.remove(session.id());
            }
        });
        registerSocketEvents();
        im.xz.cn.lingconsole.common.addon.AddonSocketSupport.apply(socketIOServer, addonSocketRegistry,
                conn -> isAuthenticated(conn.sessionId()));
        appManager.start();

        terminalCleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                java.util.List<String> dropped = passportManager.cleanup();
                for (String terminalId : dropped) {
                    terminalService.remove(terminalId);
                }
            } catch (Exception e) {
                log.debug("清理过期终端失败: {}", e.getMessage());
            }
        }, 30, 30, java.util.concurrent.TimeUnit.SECONDS);

        final String prefix = Constants.DAMON_API_PREFIX;

        javalin = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.http.maxRequestSize = 1024L * 1024 * 512; 

            
            cfg.routes.before(prefix + "/*", ctx -> {
                String path = ctx.path();
                if (path.endsWith("/auth/check") || path.endsWith("/health")) {
                    return;
                }
                
                String key = ctx.header("X-LingConsole-Key");
                if (!authManager.verify(key)) {
                    throw ApiExceptionHolder.unauthorized();
                }
            });

            registerRestApi(cfg.routes);
            socketIOServer.register(cfg.routes);

            
            cfg.routes.get("/", ctx -> ctx.html(ROOT_PAGE));

            
            if (addonManager != null) {
                registerAddonDispatchers(cfg.routes);
            }

            
            cfg.routes.exception(UnauthorizedException.class, (e, ctx) -> {
                ctx.status(401);
                ctx.json(ApiResponse.error(401, "未认证: Key 无效"));
            });
            cfg.routes.exception(Exception.class, (e, ctx) -> {
                log.error("Daemon REST 错误: {}", ctx.path(), e);
                ctx.status(500);
                ctx.json(ApiResponse.error(500, ErrorMessageUtil.friendly(e)));
            });
        });

        javalin.start(config.host(), config.port());
        log.info("Daemon 已启动: ws://{}:{}, name={}", config.host(), config.port(), config.name());
    }

    public void stop() {
        terminalCleanupExecutor.shutdownNow();
        if (socketIOServer != null) {
            socketIOServer.stop();
        }
        if (appManager != null) {
            appManager.stop();
        }
        if (terminalService != null) {
            terminalService.stop();
        }
        if (javalin != null) {
            javalin.stop();
        }
    }

    public boolean isAuthenticated(String sid) {
        return authenticatedSessions.contains(sid);
    }

    
    
    

    private void registerSocketEvents() {
        
        socketIOServer.registerNamespace(Constants.DAMON_NS);
        socketIOServer.on(Constants.DAMON_NS, "auth", (conn, event, data) -> handleAuth(conn, data));
        socketIOServer.on(Constants.DAMON_NS, "system:info", (conn, event, data) -> {
            if (!isAuthenticated(conn.sessionId())) {
                conn.emit("system:info", SocketIOResponse.error(data, 401, "未认证"));
                return;
            }
            SystemInfo info = SystemInfo.collect();
            info.setCpuUsage(monitorService.currentCpuUsage());
            conn.emit("system:info", SocketIOResponse.ok(data, info));
        });

        
        socketIOServer.on(Constants.DAMON_NS, "monitor:stats", (conn, event, data) -> requireAuth(conn, event, data, () ->
                conn.emit("monitor:stats", SocketIOResponse.ok(data, monitorService.snapshot()))));

        
        socketIOServer.on(Constants.DAMON_NS, "app:list", (conn, event, data) -> requireAuth(conn, event, data, () ->
                conn.emit("app:list", SocketIOResponse.ok(data, appManager.list()))));
        socketIOServer.on(Constants.DAMON_NS, "app:get", (conn, event, data) -> requireAuth(conn, event, data, () ->
                conn.emit("app:get", SocketIOResponse.ok(data, appManager.get(SocketIOResponse.extract(data, "id"))))));
        socketIOServer.on(Constants.DAMON_NS, "app:create", (conn, event, data) -> requireAuth(conn, event, data, () ->
                handleAppCreate(conn, data)));
        socketIOServer.on(Constants.DAMON_NS, "app:update", (conn, event, data) -> requireAuth(conn, event, data, () ->
                handleAppUpdate(conn, data)));
        socketIOServer.on(Constants.DAMON_NS, "app:delete", (conn, event, data) -> requireAuth(conn, event, data, () -> {
            String id = SocketIOResponse.extract(data, "id");
            boolean ok = appManager.delete(id);
            conn.emit("app:delete", ok
                    ? SocketIOResponse.ok(data, Map.of("deleted", id))
                    : SocketIOResponse.error(data, 404, "应用不存在: " + id));
        }));
        socketIOServer.on(Constants.DAMON_NS, "app:start", (conn, event, data) -> requireAuth(conn, event, data, () ->
                handleAppControl(conn, data, "start")));
        socketIOServer.on(Constants.DAMON_NS, "app:stop", (conn, event, data) -> requireAuth(conn, event, data, () ->
                handleAppControl(conn, data, "stop")));
        socketIOServer.on(Constants.DAMON_NS, "app:restart", (conn, event, data) -> requireAuth(conn, event, data, () ->
                handleAppControl(conn, data, "restart")));
        socketIOServer.on(Constants.DAMON_NS, "app:status", (conn, event, data) -> requireAuth(conn, event, data, () ->
                conn.emit("app:status", SocketIOResponse.ok(data, appManager.status(SocketIOResponse.extract(data, "id"))))));
        socketIOServer.on(Constants.DAMON_NS, "app:log", (conn, event, data) -> requireAuth(conn, event, data, () -> {
            String id = SocketIOResponse.extract(data, "id");
            int count = parseCount(SocketIOResponse.extract(data, "count"), 200);
            conn.emit("app:log", SocketIOResponse.ok(data, Map.of("id", id, "logs", appManager.logs(id, count))));
        }));

        
        socketIOServer.on(Constants.DAMON_NS, "passport:register", (conn, event, data) -> requireAuth(conn, event, data, () -> {
            String appId = SocketIOResponse.extract(data, "appId");
            int cols = parseCount(SocketIOResponse.extract(data, "cols"), 80);
            int rows = parseCount(SocketIOResponse.extract(data, "rows"), 24);
            TerminalService.TerminalSession session;
            if (appId != null && !appId.isBlank()) {
                AppProcess proc = appManager.getProcess(appId);
                if (proc == null || !proc.isRunning()) {
                    conn.emit("passport:register", SocketIOResponse.error(data, 409, "应用未运行"));
                    return;
                }
                session = terminalService.createAppTerminal(proc);
            } else {
                session = terminalService.createShell(cols, rows);
            }
            String passport = passportManager.register(session.id());
            conn.emit("passport:register", SocketIOResponse.ok(data, Map.of(
                    "passport", passport,
                    "terminalId", session.id(),
                    "description", session.description())));
        }));

        
        socketIOServer.registerNamespace(Constants.STREAM_NS);
        socketIOServer.onConnect(Constants.STREAM_NS, this::handleStreamConnect);
        socketIOServer.on(Constants.STREAM_NS, "terminal:input", this::handleStreamInput);
        socketIOServer.on(Constants.STREAM_NS, "terminal:resize", this::handleStreamResize);
        socketIOServer.on(Constants.STREAM_NS, "terminal:close", this::handleStreamClose);
    }

    
    
    

    private void handleStreamConnect(SocketIOConnection conn, String query) {
        String passport = extractQueryParam(query, "passport");
        String terminalId = passportManager.consume(passport);
        TerminalService.TerminalSession session = terminalId == null ? null : terminalService.get(terminalId);
        if (session == null) {
            conn.emit("auth", Map.of("status", 401, "message", "票据无效或已过期"));
            conn.close();
            return;
        }
        streamBindings.put(conn.sessionId(), session);
        session.setOutputListener(new TerminalService.OutputListener() {
            @Override
            public void onOutput(String data) {
                conn.emit("terminal:output", Map.of("data", data));
            }

            @Override
            public void onExit() {
                conn.emit("terminal:exit", Map.of());
            }

            @Override
            public void onStatus(boolean running) {
                conn.emit("terminal:status", Map.of("running", running));
            }
        });
        conn.emit("auth", Map.of("status", 200, "message", "success",
                "data", Map.of("terminalId", terminalId)));

        
        conn.emit("terminal:status", Map.of("running", session.canInput()));

        
        List<String> recent = session.recentLines();
        if (!recent.isEmpty()) {
            conn.emit("terminal:output", Map.of("data", String.join("\r\n", recent) + "\r\n"));
        }
        log.info("终端流已连接: sid={}, {}", conn.sessionId(), session.description());
    }

    private void handleStreamInput(SocketIOConnection conn, String event, Object data) {
        TerminalService.TerminalSession session = streamBindings.get(conn.sessionId());
        if (session == null) {
            return;
        }
        if (data instanceof JsonNode node) {
            session.writeInput(node.path("data").asText(""));
        }
    }

    private void handleStreamResize(SocketIOConnection conn, String event, Object data) {
        TerminalService.TerminalSession session = streamBindings.get(conn.sessionId());
        if (session == null) {
            return;
        }
        if (data instanceof JsonNode node) {
            int cols = node.path("cols").asInt(80);
            int rows = node.path("rows").asInt(24);
            session.resize(cols, rows);
        }
    }

    private void handleStreamClose(SocketIOConnection conn, String event, Object data) {
        TerminalService.TerminalSession session = streamBindings.remove(conn.sessionId());
        if (session != null) {
            terminalService.remove(session.id());
        }
    }

    private String extractQueryParam(String query, String key) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void requireAuth(SocketIOConnection conn, String event, Object data, Runnable action) {
        if (!isAuthenticated(conn.sessionId())) {
            conn.emit(event, SocketIOResponse.error(data, 401, "未认证"));
            return;
        }
        action.run();
    }

    private void handleAppCreate(SocketIOConnection conn, Object data) {
        AppRequest req = parseAppRequest(data);
        if (req.id == null || !req.id.matches("[a-z0-9]+")) {
            conn.emit("app:create", SocketIOResponse.error(data, 400, "应用 ID 仅允许小写英文字母和阿拉伯数字"));
            return;
        }
        if (req.name == null || req.command == null || req.command.isBlank()) {
            conn.emit("app:create", SocketIOResponse.error(data, 400, "缺少 name 或 command"));
            return;
        }
        try {
            AppInfo info = appManager.create(req.id, req.name, req.command, req.type,
                    req.autoStart, req.autoRestart, req.maxRestartCount, req.args, req.environment);
            conn.emit("app:create", SocketIOResponse.ok(data, info));
        } catch (Exception e) {
            conn.emit("app:create", SocketIOResponse.error(data, 500, ErrorMessageUtil.friendly(e)));
        }
    }

    private void handleAppUpdate(SocketIOConnection conn, Object data) {
        AppRequest req = parseAppRequest(data);
        if (req.id == null) {
            conn.emit("app:update", SocketIOResponse.error(data, 400, "缺少 id"));
            return;
        }
        try {
            AppInfo info = appManager.update(req.id, req.name, req.command, req.type,
                    req.autoStart, req.autoRestart, req.maxRestartCount, req.args, req.environment,
                    req.workDir, req.encoding, req.ptyType, req.runAsUser);
            conn.emit("app:update", info != null
                    ? SocketIOResponse.ok(data, info)
                    : SocketIOResponse.error(data, 404, "应用不存在"));
        } catch (Exception e) {
            conn.emit("app:update", SocketIOResponse.error(data, 500, ErrorMessageUtil.friendly(e)));
        }
    }

    private void handleAppControl(SocketIOConnection conn, Object data, String action) {
        String id = SocketIOResponse.extract(data, "id");
        if (id == null) {
            conn.emit("app:" + action, SocketIOResponse.error(data, 400, "缺少 id"));
            return;
        }
        AppInfo info = switch (action) {
            case "start" -> appManager.start(id);
            case "stop" -> appManager.stop(id);
            case "restart" -> appManager.restart(id);
            default -> null;
        };
        conn.emit("app:" + action, info != null
                ? SocketIOResponse.ok(data, info)
                : SocketIOResponse.error(data, 404, "应用不存在: " + id));
    }

    private AppRequest parseAppRequest(Object data) {
        AppRequest req = new AppRequest();
        if (data instanceof JsonNode node) {
            JsonNode inner = node.path("data");
            if (inner.isObject()) {
                req.id = inner.path("id").asText(null);
                req.name = inner.path("name").asText(null);
                req.command = inner.path("command").asText(null);
                req.type = inner.path("type").asText("general");
                req.autoStart = inner.path("autoStart").asBoolean(false);
                req.autoRestart = inner.path("autoRestart").asBoolean(false);
                req.maxRestartCount = inner.path("maxRestartCount").asInt(0);
                req.workDir = inner.path("workDir").asText(null);
                req.encoding = inner.path("encoding").asText(null);
                req.ptyType = inner.path("ptyType").asText(null);
                req.runAsUser = inner.path("runAsUser").asText(null);
                if (inner.path("args").isArray()) {
                    List<String> args = new java.util.ArrayList<>();
                    inner.path("args").forEach(a -> args.add(a.asText()));
                    req.args = args;
                }
                if (inner.path("environment").isObject()) {
                    Map<String, String> env = new java.util.HashMap<>();
                    inner.path("environment").properties().forEach(e -> env.put(e.getKey(), e.getValue().asText()));
                    req.environment = env;
                }
            }
        }
        return req;
    }

    private int parseCount(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void handleAuth(SocketIOConnection conn, Object data) {
        String key = SocketIOResponse.extract(data, "key");
        if (authManager.verify(key)) {
            authenticatedSessions.add(conn.sessionId());
            conn.emit("auth", SocketIOResponse.ok(data, Map.of("sid", conn.sessionId())));
            log.info("Daemon 控制面认证成功: sid={}", conn.sessionId());
            return;
        }
        conn.emit("auth", SocketIOResponse.error(data, 401, "Key 无效"));
        log.warn("Daemon 控制面认证失败: sid={}", conn.sessionId());
    }

    
    
    

    private void registerRestApi(io.javalin.config.RoutesConfig routes) {
        String prefix = Constants.DAMON_API_PREFIX;

        
        routes.get(prefix + "/auth/check", ctx -> {
            String key = ctx.header("X-LingConsole-Key");
            boolean ok = authManager.verify(key);
            ctx.json(ok ? ApiResponse.ok(Map.of("name", config.name())) : ApiResponse.error(401, "Key 无效"));
        });

        
        routes.get(prefix + "/system/info", ctx -> {
            SystemInfo info = SystemInfo.collect();
            info.setCpuUsage(monitorService.currentCpuUsage());
            ctx.json(ApiResponse.ok(info));
        });

        
        routes.get(prefix + "/monitor", ctx -> ctx.json(ApiResponse.ok(monitorService.snapshot())));

        
        routes.get(prefix + "/health", ctx ->
                ctx.json(ApiResponse.ok(Map.of("status", "ok", "name", config.name()))));

        
        routes.get(prefix + "/apps", ctx -> ctx.json(ApiResponse.ok(appManager.list())));

        routes.post(prefix + "/apps", ctx -> {
            AppRequestRest req = ctx.bodyAsClass(AppRequestRest.class);
            if (req.id == null || !req.id.matches("[a-z0-9]+")) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, "应用 ID 仅允许小写英文字母和阿拉伯数字"));
                return;
            }
            if (req.name == null || req.command == null || req.command.isBlank()) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, "缺少 name 或 command"));
                return;
            }
            AppInfo info = appManager.create(req.id, req.name, req.command, req.type,
                    req.autoStart, req.autoRestart, req.maxRestartCount, req.args, req.environment);
            ctx.json(ApiResponse.ok(info));
        });

        routes.get(prefix + "/apps/{id}", ctx -> {
            AppInfo info = appManager.get(ctx.pathParam("id"));
            if (info == null) {
                ctx.status(404);
                ctx.json(ApiResponse.error(404, "应用不存在"));
                return;
            }
            ctx.json(ApiResponse.ok(info));
        });

        routes.put(prefix + "/apps/{id}", ctx -> {
            AppRequestRest req = ctx.bodyAsClass(AppRequestRest.class);
            AppInfo info = appManager.update(ctx.pathParam("id"), req.name, req.command, req.type,
                    req.autoStart, req.autoRestart, req.maxRestartCount, req.args, req.environment,
                    req.workDir, req.encoding, req.ptyType, req.runAsUser);
            if (info == null) {
                ctx.status(404);
                ctx.json(ApiResponse.error(404, "应用不存在"));
                return;
            }
            ctx.json(ApiResponse.ok(info));
        });

        routes.delete(prefix + "/apps/{id}", ctx -> {
            boolean ok = appManager.delete(ctx.pathParam("id"));
            ctx.json(ok ? ApiResponse.ok() : ApiResponse.error(404, "应用不存在"));
        });

        routes.post(prefix + "/apps/{id}/start", ctx -> ctx.json(ApiResponse.ok(appManager.start(ctx.pathParam("id")))));
        routes.post(prefix + "/apps/{id}/stop", ctx -> ctx.json(ApiResponse.ok(appManager.stop(ctx.pathParam("id")))));
        routes.post(prefix + "/apps/{id}/restart", ctx -> ctx.json(ApiResponse.ok(appManager.restart(ctx.pathParam("id")))));
        routes.get(prefix + "/apps/{id}/status", ctx -> ctx.json(ApiResponse.ok(appManager.status(ctx.pathParam("id")))));
        routes.get(prefix + "/apps/{id}/logs", ctx -> {
            int count = ctx.queryParamAsClass("count", Integer.class).getOrDefault(200);
            ctx.json(ApiResponse.ok(Map.of("id", ctx.pathParam("id"), "logs", appManager.logs(ctx.pathParam("id"), count))));
        });

        
        routes.post(prefix + "/apps/{id}/signal", ctx -> {
            try {
                SignalRequest req = ctx.bodyAsClass(SignalRequest.class);
                String signal = req.signal == null || req.signal.isBlank() ? "SIGTERM" : req.signal.trim().toUpperCase();
                if (!ALLOWED_SIGNALS.contains(signal)) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "不支持的信号: " + signal));
                    return;
                }
                if (isDaemonWindows()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "Windows 不支持进程信号"));
                    return;
                }
                AppProcess proc = appManager.getProcess(ctx.pathParam("id"));
                if (proc == null || proc.pid() == null) {
                    ctx.status(404);
                    ctx.json(ApiResponse.error(404, "应用未运行"));
                    return;
                }
                String sig = signal.startsWith("SIG") ? signal.substring(3) : signal;
                Process kill = new ProcessBuilder("kill", "-" + sig, String.valueOf(proc.pid()))
                        .redirectErrorStream(true)
                        .start();
                String out = new String(kill.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (kill.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && kill.exitValue() == 0) {
                    ctx.json(ApiResponse.ok(Map.of("pid", proc.pid(), "signal", signal)));
                } else {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "发送信号失败: " + out));
                }
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/files/7zip/status", ctx -> {
            try {
                String bin = zipBinary();
                ctx.json(ApiResponse.ok(Map.of(
                        "installed", !bin.isBlank(),
                        "binary", bin)));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/files/7zip/install", ctx -> {
            try {
                if (isDaemonWindows()) {
                    ctx.json(ApiResponse.ok(Map.of(
                            "exitCode", -1, "stdout", "", "stderr",
                            "Windows 系统不支持自动安装 7zip, 请手动安装 7-Zip", "timedOut", false)));
                    return;
                }
                Map<String, Object> r = runShell(AUTO_INSTALL_7ZIP, 120000);
                ctx.json(ApiResponse.ok(r));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/files/archive/compress", ctx -> {
            try {
                ArchiveRequest req = ctx.bodyAsClass(ArchiveRequest.class);
                if (req.files == null || req.files.isEmpty() || req.archive == null || req.archive.isBlank()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "缺少 files 或 archive"));
                    return;
                }
                String bin = zipBinary();
                if (bin.isBlank()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "7zip未安装"));
                    return;
                }
                StringBuilder sb = new StringBuilder(bin).append(" a -y ").append(quote(req.archive));
                for (String f : req.files) {
                    sb.append(' ').append(quote(f));
                }
                ctx.json(ApiResponse.ok(runShell(sb.toString(), 120000)));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/files/archive/extract", ctx -> {
            try {
                ArchiveRequest req = ctx.bodyAsClass(ArchiveRequest.class);
                if (req.archive == null || req.archive.isBlank() || req.dest == null || req.dest.isBlank()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "缺少 archive 或 dest"));
                    return;
                }
                String bin = zipBinary();
                if (bin.isBlank()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "7zip未安装"));
                    return;
                }
                String cmd = bin + " x -y " + quote(req.archive) + " -o" + quote(req.dest);
                ctx.json(ApiResponse.ok(runShell(cmd, 120000)));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        
        routes.post(prefix + "/exec", ctx -> {
            if (!EXEC_SEMAPHORE.tryAcquire()) {
                ctx.status(429);
                ctx.json(ApiResponse.error(429, "exec 并发过多, 请稍后重试"));
                return;
            }
            try {
                ExecRequest req = ctx.bodyAsClass(ExecRequest.class);
                if (req.command == null || req.command.isBlank()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "缺少 command"));
                    return;
                }
                long timeout = req.timeoutMs <= 0 ? 30_000 : Math.min(req.timeoutMs, 120_000);
                List<String> cmd = isDaemonWindows()
                        ? List.of("cmd", "/c", req.command)
                        : List.of("sh", "-c", req.command);
                Process p = new ProcessBuilder(cmd).start();
                var stdout = new java.io.ByteArrayOutputStream();
                var stderr = new java.io.ByteArrayOutputStream();
                Thread ot = new Thread(() -> {
                    try {
                        p.getInputStream().transferTo(stdout);
                    } catch (java.io.IOException ignored) {
                    }
                }, "exec-stdout");
                Thread et = new Thread(() -> {
                    try {
                        p.getErrorStream().transferTo(stderr);
                    } catch (java.io.IOException ignored) {
                    }
                }, "exec-stderr");
                ot.setDaemon(true);
                et.setDaemon(true);
                ot.start();
                et.start();
                boolean finished = p.waitFor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (!finished) {
                    p.destroyForcibly();
                    ctx.json(ApiResponse.ok(Map.of(
                            "exitCode", -1,
                            "stdout", stdout.toString(StandardCharsets.UTF_8),
                            "stderr", "执行超时 (> " + timeout + "ms)",
                            "timedOut", true)));
                    return;
                }
                ot.join(2000);
                et.join(2000);
                ctx.json(ApiResponse.ok(Map.of(
                        "exitCode", p.exitValue(),
                        "stdout", stdout.toString(StandardCharsets.UTF_8),
                        "stderr", stderr.toString(StandardCharsets.UTF_8),
                        "timedOut", false)));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            } finally {
                EXEC_SEMAPHORE.release();
            }
        });

        
        routes.post(prefix + "/terminal/passport", ctx -> {
            TerminalPassportRequest req = ctx.bodyAsClass(TerminalPassportRequest.class);
            TerminalService.TerminalSession session;
            if (req.appId != null && !req.appId.isBlank()) {
                AppProcess proc = appManager.getProcess(req.appId);
                if (proc == null || !proc.isRunning()) {
                    ctx.status(409);
                    ctx.json(ApiResponse.error(409, "应用未运行"));
                    return;
                }
                session = terminalService.createAppTerminal(proc);
            } else {
                session = terminalService.createShell(req.cols <= 0 ? 80 : req.cols,
                        req.rows <= 0 ? 24 : req.rows);
            }
            String passport = passportManager.register(session.id());
            ctx.json(ApiResponse.ok(Map.of(
                    "passport", passport,
                    "terminalId", session.id(),
                    "description", session.description())));
        });

        
        routes.get(prefix + "/files/list", ctx -> {
            try {
                ctx.json(ApiResponse.ok(fileSystemService.list(ctx.queryParam("path"))));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.get(prefix + "/files/read", ctx -> {
            try {
                ctx.json(ApiResponse.ok(Map.of("content", fileSystemService.read(ctx.queryParam("path")))));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/files/write", ctx -> {
            try {
                WriteRequest req = ctx.bodyAsClass(WriteRequest.class);
                fileSystemService.write(req.path, req.content);
                ctx.json(ApiResponse.ok());
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/files/mkdir", ctx -> {
            try {
                fileSystemService.mkdir(ctx.queryParam("path"));
                ctx.json(ApiResponse.ok());
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.delete(prefix + "/files", ctx -> {
            try {
                boolean deleted = fileSystemService.delete(ctx.queryParam("path"));
                ctx.json(ApiResponse.ok(Map.of("deleted", deleted)));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        
        routes.post(prefix + "/files/upload", ctx -> {
            try {
                String path = ctx.queryParam("path");
                String filename = ctx.queryParam("filename");
                var uploaded = ctx.uploadedFiles("file");
                if (uploaded.isEmpty()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "缺少文件"));
                    return;
                }
                var file = uploaded.getFirst();
                String name = (filename == null || filename.isBlank()) ? file.filename() : filename;
                validateFilename(name);
                java.nio.file.Path target = FileSystemService.resolvePath(path)
                        .resolve(name).normalize();
                try (InputStream in = file.content()) {
                    java.nio.file.Files.copy(in, target,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                ctx.json(ApiResponse.ok(Map.of("path", target.toString())));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        
        routes.post(prefix + "/files/upload-raw", ctx -> {
            try {
                String path = ctx.queryParam("path");
                String filename = ctx.queryParam("filename");
                if (filename == null || filename.isBlank()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "缺少 filename"));
                    return;
                }
                validateFilename(filename);
                java.nio.file.Path target = FileSystemService.resolvePath(path)
                        .resolve(filename).normalize();
                java.nio.file.Files.createDirectories(target.getParent());
                try (InputStream in = ctx.bodyInputStream()) {
                    java.nio.file.Files.copy(in, target,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                ctx.json(ApiResponse.ok(Map.of("path", target.toString())));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        
        routes.get(prefix + "/files/download", ctx -> {
            try {
                java.nio.file.Path file = FileSystemService.resolvePath(ctx.queryParam("path"));
                if (!java.nio.file.Files.isRegularFile(file)) {
                    ctx.status(404);
                    ctx.json(ApiResponse.error(404, "文件不存在"));
                    return;
                }
                ctx.contentType("application/octet-stream");
                ctx.header("Content-Disposition", "attachment; filename=\""
                        + im.xz.cn.lingconsole.common.util.PathUtil.safeFileName(file) + "\"");
                ctx.result(java.nio.file.Files.newInputStream(file));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        
        routes.post(prefix + "/files/rename", ctx -> {
            try {
                fileSystemService.rename(ctx.queryParam("path"), ctx.queryParam("newName"));
                ctx.json(ApiResponse.ok());
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/files/copy", ctx -> {
            try {
                fileSystemService.copy(ctx.queryParam("path"), ctx.queryParam("dest"));
                ctx.json(ApiResponse.ok());
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        
        routes.get(prefix + "/files/drives", ctx -> {
            boolean windows = isDaemonWindows();
            ctx.json(ApiResponse.ok(Map.of(
                    "os", windows ? "windows" : "linux",
                    "drives", fileSystemService.listDrives())));
        });

        
        registerAppFilesApi(routes, prefix);
    }

    
    private void registerAppFilesApi(io.javalin.config.RoutesConfig routes, String prefix) {
        routes.get(prefix + "/apps/{id}/files/list", ctx -> {
            try {
                String wd = requireAppWorkDir(ctx.pathParam("id"));
                ctx.json(ApiResponse.ok(fileSystemService.listUnderRelative(wd, ctx.queryParam("path"))));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.get(prefix + "/apps/{id}/files/read", ctx -> {
            try {
                String wd = requireAppWorkDir(ctx.pathParam("id"));
                ctx.json(ApiResponse.ok(Map.of("content", fileSystemService.readUnder(wd, ctx.queryParam("path")))));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/apps/{id}/files/write", ctx -> {
            try {
                String wd = requireAppWorkDir(ctx.pathParam("id"));
                WriteRequest req = ctx.bodyAsClass(WriteRequest.class);
                fileSystemService.writeUnder(wd, req.path, req.content);
                ctx.json(ApiResponse.ok());
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/apps/{id}/files/mkdir", ctx -> {
            try {
                String wd = requireAppWorkDir(ctx.pathParam("id"));
                fileSystemService.mkdirUnder(wd, ctx.queryParam("path"));
                ctx.json(ApiResponse.ok());
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.delete(prefix + "/apps/{id}/files", ctx -> {
            try {
                String wd = requireAppWorkDir(ctx.pathParam("id"));
                boolean deleted = fileSystemService.deleteUnder(wd, ctx.queryParam("path"));
                ctx.json(ApiResponse.ok(Map.of("deleted", deleted)));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/apps/{id}/files/upload-raw", ctx -> {
            try {
                String wd = requireAppWorkDir(ctx.pathParam("id"));
                String filename = ctx.queryParam("filename");
                if (filename == null || filename.isBlank()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "缺少 filename"));
                    return;
                }
                validateFilename(filename);
                String up = ctx.queryParam("path");
                String rel = (up == null || up.isBlank()) ? filename : up + "/" + filename;
                java.nio.file.Path target = FileSystemService.resolveSandboxed(wd, rel);
                java.nio.file.Files.createDirectories(target.getParent());
                try (InputStream in = ctx.bodyInputStream()) {
                    java.nio.file.Files.copy(in, target,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                ctx.json(ApiResponse.ok(Map.of("path", target.toString())));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.get(prefix + "/apps/{id}/files/download", ctx -> {
            try {
                String wd = requireAppWorkDir(ctx.pathParam("id"));
                java.nio.file.Path file = FileSystemService.resolveSandboxed(wd, ctx.queryParam("path"));
                if (!java.nio.file.Files.isRegularFile(file)) {
                    ctx.status(404);
                    ctx.json(ApiResponse.error(404, "文件不存在"));
                    return;
                }
                ctx.contentType("application/octet-stream");
                ctx.header("Content-Disposition", "attachment; filename=\""
                        + im.xz.cn.lingconsole.common.util.PathUtil.safeFileName(file) + "\"");
                ctx.result(java.nio.file.Files.newInputStream(file));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/apps/{id}/files/rename", ctx -> {
            try {
                String wd = requireAppWorkDir(ctx.pathParam("id"));
                fileSystemService.renameUnder(wd, ctx.queryParam("path"), ctx.queryParam("newName"));
                ctx.json(ApiResponse.ok());
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/apps/{id}/files/copy", ctx -> {
            try {
                String wd = requireAppWorkDir(ctx.pathParam("id"));
                fileSystemService.copyUnder(wd, ctx.queryParam("path"), ctx.queryParam("dest"));
                ctx.json(ApiResponse.ok());
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/apps/{id}/files/archive/compress", ctx -> {
            try {
                String wd = requireAppWorkDir(ctx.pathParam("id"));
                ArchiveRequest req = ctx.bodyAsClass(ArchiveRequest.class);
                if (req.files == null || req.files.isEmpty() || req.archive == null || req.archive.isBlank()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "缺少 files 或 archive"));
                    return;
                }
                String bin = zipBinary();
                if (bin.isBlank()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "7zip未安装"));
                    return;
                }
                StringBuilder sb = new StringBuilder(bin).append(" a -y ")
                        .append(quote(FileSystemService.resolveSandboxed(wd, req.archive).toString()));
                for (String f : req.files) {
                    sb.append(' ').append(quote(FileSystemService.resolveSandboxed(wd, f).toString()));
                }
                ctx.json(ApiResponse.ok(runShell(sb.toString(), 120000)));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });

        routes.post(prefix + "/apps/{id}/files/archive/extract", ctx -> {
            try {
                String wd = requireAppWorkDir(ctx.pathParam("id"));
                ArchiveRequest req = ctx.bodyAsClass(ArchiveRequest.class);
                if (req.archive == null || req.archive.isBlank() || req.dest == null || req.dest.isBlank()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "缺少 archive 或 dest"));
                    return;
                }
                String bin = zipBinary();
                if (bin.isBlank()) {
                    ctx.status(400);
                    ctx.json(ApiResponse.error(400, "7zip未安装"));
                    return;
                }
                String cmd = bin + " x -y " + quote(FileSystemService.resolveSandboxed(wd, req.archive).toString())
                        + " -o" + quote(FileSystemService.resolveSandboxed(wd, req.dest).toString());
                ctx.json(ApiResponse.ok(runShell(cmd, 120000)));
            } catch (Exception e) {
                ctx.status(400);
                ctx.json(ApiResponse.error(400, ErrorMessageUtil.friendly(e)));
            }
        });
    }

    private Map<String, Object> runShell(String command, long timeoutMs) {
        long timeout = timeoutMs <= 0 ? 30_000 : Math.min(timeoutMs, 120_000);
        List<String> cmd = isDaemonWindows()
                ? List.of("cmd", "/c", command)
                : List.of("sh", "-c", command);
        try {
            Process p = new ProcessBuilder(cmd).start();
            var stdout = new java.io.ByteArrayOutputStream();
            var stderr = new java.io.ByteArrayOutputStream();
            Thread ot = new Thread(() -> {
                try {
                    p.getInputStream().transferTo(stdout);
                } catch (java.io.IOException ignored) {
                }
            }, "exec-stdout");
            Thread et = new Thread(() -> {
                try {
                    p.getErrorStream().transferTo(stderr);
                } catch (java.io.IOException ignored) {
                }
            }, "exec-stderr");
            ot.setDaemon(true);
            et.setDaemon(true);
            ot.start();
            et.start();
            boolean finished = p.waitFor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return Map.of(
                        "exitCode", -1,
                        "stdout", stdout.toString(StandardCharsets.UTF_8),
                        "stderr", "执行超时 (> " + timeout + "ms)",
                        "timedOut", true);
            }
            ot.join(2000);
            et.join(2000);
            return Map.of(
                    "exitCode", p.exitValue(),
                    "stdout", stdout.toString(StandardCharsets.UTF_8),
                    "stderr", stderr.toString(StandardCharsets.UTF_8),
                    "timedOut", false);
        } catch (Exception e) {
            return Map.of(
                    "exitCode", -2,
                    "stdout", "",
                    "stderr", ErrorMessageUtil.friendly(e),
                    "timedOut", false);
        }
    }

    private String zipBinary() {
        String probe = isDaemonWindows()
                ? "where 7z"
                : "if command -v 7z >/dev/null 2>&1; then echo 7z; elif command -v 7zz >/dev/null 2>&1; then echo 7zz; elif command -v 7za >/dev/null 2>&1; then echo 7za; else echo none; fi";
        Map<String, Object> r = runShell(probe, 8000);
        String out = String.valueOf(r.get("stdout")).trim();
        if (out.isBlank() || "none".equals(out)) {
            return "";
        }
        if (out.contains("7z")) {
            return out.contains("7zz") ? "7zz" : out.contains("7za") ? "7za" : "7z";
        }
        return "";
    }

    private String quote(String s) {
        if (s == null) {
            return "\"\"";
        }
        if (isDaemonWindows()) {
            return "\"" + s.replace("\"", "^\"") + "\"";
        }
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private String requireAppWorkDir(String appId) {
        String wd = appManager.workDirOf(appId);
        if (wd == null) {
            throw new IllegalStateException("应用不存在或未设置工作目录");
        }
        return wd;
    }

    
    
    

    private void registerAddonDispatchers(io.javalin.config.RoutesConfig routes) {
        String prefix = Constants.DAMON_API_PREFIX + "/addon/*";
        routes.get(prefix, this::dispatchAddon);
        routes.post(prefix, this::dispatchAddon);
        routes.put(prefix, this::dispatchAddon);
        routes.delete(prefix, this::dispatchAddon);
        routes.patch(prefix, this::dispatchAddon);
    }

    private void dispatchAddon(Context ctx) {
        String path = ctx.path();
        String rest = path.substring(Constants.DAMON_API_PREFIX.length() + "/addon/".length());
        int slash = rest.indexOf('/');
        String name = slash == -1 ? rest : rest.substring(0, slash);
        String sub = slash == -1 ? "/" : rest.substring(slash);
        var context = addonManager == null ? null : addonManager.contextOf(name);
        if (context == null) {
            ctx.status(404);
            ctx.json(ApiResponse.error(404, "Not Found"));
            return;
        }
        im.xz.cn.lingconsole.addon.AddonRouteHandler handler = context.daemonRouteHandler(ctx.method().name(), sub);
        if (handler == null) {
            ctx.status(404);
            ctx.json(ApiResponse.error(404, "Not Found"));
            return;
        }
        handler.handle(ctx);
    }

    
    private void validateFilename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IllegalArgumentException("文件名非法: " + name);
        }
    }

    private static boolean isDaemonWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    
    public static class AppRequest {
        String id;
        String name;
        String command;
        String type;
        boolean autoStart;
        boolean autoRestart;
        int maxRestartCount;
        List<String> args;
        Map<String, String> environment;
        String workDir;
        String encoding;
        String ptyType;
        String runAsUser;
    }

    
    public record AppRequestRest(@JsonProperty("id") String id,
                                 @JsonProperty("name") String name,
                                 @JsonProperty("command") String command,
                                 @JsonProperty("type") String type,
                                 @JsonProperty("autoStart") boolean autoStart,
                                 @JsonProperty("autoRestart") boolean autoRestart,
                                 @JsonProperty("maxRestartCount") int maxRestartCount,
                                 @JsonProperty("workDir") String workDir,
                                 @JsonProperty("encoding") String encoding,
                                 @JsonProperty("ptyType") String ptyType,
                                 @JsonProperty("args") List<String> args,
                                 @JsonProperty("environment") Map<String, String> environment,
                                 @JsonProperty("runAsUser") String runAsUser) {
    }

    
    public record TerminalPassportRequest(@JsonProperty("appId") String appId,
                                          @JsonProperty("cols") int cols,
                                          @JsonProperty("rows") int rows) {
    }

    public record ArchiveRequest(@JsonProperty("files") List<String> files,
                                 @JsonProperty("archive") String archive,
                                 @JsonProperty("dest") String dest) {
    }

    
    public record WriteRequest(@JsonProperty("path") String path,
                               @JsonProperty("content") String content) {
    }

    
    public record SignalRequest(@JsonProperty("signal") String signal) {
    }

    
    public record ExecRequest(@JsonProperty("command") String command,
                              @JsonProperty("timeoutMs") long timeoutMs) {
    }

    
    public static final class UnauthorizedException extends RuntimeException {
    }

    private static final class ApiExceptionHolder {
        static UnauthorizedException unauthorized() {
            return new UnauthorizedException();
        }
    }
}
