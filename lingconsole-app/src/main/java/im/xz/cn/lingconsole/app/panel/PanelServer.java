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
package im.xz.cn.lingconsole.app.panel;

import im.xz.cn.lingconsole.app.panel.controller.AuthController;
import im.xz.cn.lingconsole.app.panel.controller.AppController;
import im.xz.cn.lingconsole.app.panel.controller.DashboardController;
import im.xz.cn.lingconsole.app.panel.controller.FileController;
import im.xz.cn.lingconsole.app.panel.controller.LogController;
import im.xz.cn.lingconsole.app.panel.controller.MonitorController;
import im.xz.cn.lingconsole.app.panel.controller.NodeController;
import im.xz.cn.lingconsole.app.panel.controller.PackageController;
import im.xz.cn.lingconsole.app.panel.controller.PermissionGroupController;
import im.xz.cn.lingconsole.app.panel.controller.SettingsController;
import im.xz.cn.lingconsole.app.panel.controller.TerminalController;
import im.xz.cn.lingconsole.app.panel.controller.UserController;
import im.xz.cn.lingconsole.app.panel.exception.ApiException;
import im.xz.cn.lingconsole.app.panel.middleware.AuthMiddleware;
import im.xz.cn.lingconsole.app.panel.model.Node;
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.app.panel.repository.DatabaseManager;
import im.xz.cn.lingconsole.app.panel.repository.LogRepository;
import im.xz.cn.lingconsole.app.panel.repository.NodeRepository;
import im.xz.cn.lingconsole.app.panel.repository.PermissionGroupRepository;
import im.xz.cn.lingconsole.app.panel.repository.RootAccountRepository;
import im.xz.cn.lingconsole.app.panel.repository.SessionRepository;
import im.xz.cn.lingconsole.app.panel.repository.UserGroupRepository;
import im.xz.cn.lingconsole.app.panel.repository.UserRepository;
import im.xz.cn.lingconsole.app.panel.service.LogService;
import im.xz.cn.lingconsole.app.panel.service.LoginAttemptService;
import im.xz.cn.lingconsole.app.panel.service.NodeService;
import im.xz.cn.lingconsole.app.panel.service.PermissionService;
import im.xz.cn.lingconsole.app.panel.service.SessionService;
import im.xz.cn.lingconsole.app.panel.service.UserService;
import im.xz.cn.lingconsole.app.panel.remote.DaemonConnection;
import im.xz.cn.lingconsole.app.panel.remote.DaemonHttpProxy;
import im.xz.cn.lingconsole.app.web.PageController;
import im.xz.cn.lingconsole.app.web.ThymeleafRenderer;
import im.xz.cn.lingconsole.common.config.Constants;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import im.xz.cn.lingconsole.common.model.SystemInfo;
import im.xz.cn.lingconsole.common.socketio.SocketIOServer;
import im.xz.cn.lingconsole.common.socketio.SocketIOResponse;
import im.xz.cn.lingconsole.daemon.DaemonConfig;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class PanelServer {

    private static final Logger log = LoggerFactory.getLogger(PanelServer.class);
    private final PanelConfig config;
    private final DatabaseManager db;
    private final DaemonConfig daemonConfig;
    private final String webDir;
    private final boolean singleUserMode;
    private Javalin javalin;
    private SocketIOServer socketIOServer;
    private UserService userService;
    private SessionService sessionService;
    private NodeService nodeService;
    private volatile im.xz.cn.lingconsole.app.panel.service.NodePermissionIndex nodePermissionIndex;
    private LogService logService;
    private LoginAttemptService loginAttemptService;
    private PermissionService permissionService;
    private PermissionGroupRepository permissionGroupRepository;
    private UserGroupRepository userGroupRepository;
    private final ConcurrentMap<String, im.xz.cn.lingconsole.app.panel.model.AuthUser> panelSessions = new ConcurrentHashMap<>();
    private final im.xz.cn.lingconsole.app.panel.service.TerminalTicketStore terminalTicketStore =
            new im.xz.cn.lingconsole.app.panel.service.TerminalTicketStore();
    private final ConcurrentMap<String, TerminalBridge> terminalBridges = new ConcurrentHashMap<>();

    private final im.xz.cn.lingconsole.common.addon.AddonSocketRegistry addonSocketRegistry;
    private final im.xz.cn.lingconsole.common.addon.AddonMenuRegistry addonMenuRegistry;
    private final im.xz.cn.lingconsole.common.addon.AddonProxyRegistry addonProxyRegistry;
    private volatile im.xz.cn.lingconsole.common.addon.AddonManager addonManager;

    public PanelServer(PanelConfig config, DatabaseManager db, DaemonConfig daemonConfig, String dataDir,
                       boolean singleUserMode,
                       im.xz.cn.lingconsole.common.addon.AddonSocketRegistry addonSocketRegistry,
                       im.xz.cn.lingconsole.common.addon.AddonMenuRegistry addonMenuRegistry,
                       im.xz.cn.lingconsole.common.addon.AddonProxyRegistry addonProxyRegistry) {
        this.config = config;
        this.db = db;
        this.daemonConfig = daemonConfig;
        this.webDir = dataDir + "/web";
        this.singleUserMode = singleUserMode;
        this.addonSocketRegistry = addonSocketRegistry;
        this.addonMenuRegistry = addonMenuRegistry;
        this.addonProxyRegistry = addonProxyRegistry;
        initServices();
    }

    public void setAddonManager(im.xz.cn.lingconsole.common.addon.AddonManager addonManager) {
        this.addonManager = addonManager;
    }

    public NodeService nodeService() {
        return nodeService;
    }

    public UserService userService() {
        return userService;
    }

    public LogService logService() {
        return logService;
    }

    public im.xz.cn.lingconsole.common.socketio.SocketIOServer socketIOServer() {
        return socketIOServer;
    }

    public boolean isPanelSession(String sessionId) {
        return sessionId != null && panelSessions.containsKey(sessionId);
    }

    public void start() {
        socketIOServer = new SocketIOServer();
        setupSocketEvents();
        im.xz.cn.lingconsole.common.addon.AddonSocketSupport.apply(socketIOServer, addonSocketRegistry,
                conn -> panelSessions.containsKey(conn.sessionId()));

        ThymeleafRenderer renderer = new ThymeleafRenderer(webDir + "/templates");
        final String apiPrefix = Constants.WEB_API_PREFIX;
        final AuthMiddleware authMiddleware = new AuthMiddleware(sessionService, permissionService);

        javalin = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.http.maxRequestSize = 1024L * 1024 * 256;

            
            cfg.routes.before(apiPrefix + "/*", ctx -> {
                if (!ctx.path().equals(apiPrefix + "/auth/login")) {
                    authMiddleware.handle(ctx);
                }
            });

            
            cfg.routes.after(apiPrefix + "/*", ctx -> {
                String ct = ctx.contentType();
                if (ct != null && ct.startsWith("application/json") && !ct.contains("charset")) {
                    ctx.contentType("application/json; charset=utf-8");
                }
            });

            
            cfg.routes.before(ctx -> {
                String path = ctx.path();
                if (path.equals("/login")
                        || path.startsWith("/static/")
                        || path.startsWith(Constants.SOCKET_IO_PATH)
                        || path.startsWith(Constants.WEB_API_PREFIX)) {
                    return;
                }
                if (ctx.cookie(AuthMiddleware.COOKIE_NAME) == null) {
                    
                    ctx.redirect("/login");
                    throw new PageRedirectException();
                }
            });

            
            new AuthController(userService, sessionService, logService, loginAttemptService, singleUserMode).register(cfg.routes, apiPrefix);
            new NodeController(nodeService, logService, nodePermissionIndex).register(cfg.routes, apiPrefix);
            new AppController(nodeService, logService, nodePermissionIndex).register(cfg.routes, apiPrefix);
            new DashboardController(nodeService).register(cfg.routes, apiPrefix);
            new LogController(logService).register(cfg.routes, apiPrefix);
            new MonitorController(nodeService).register(cfg.routes, apiPrefix);
            new SettingsController(config, daemonConfig).register(cfg.routes, apiPrefix);
            if (!singleUserMode) {
                new UserController(userService, permissionService, userGroupRepository, logService, sessionService).register(cfg.routes, apiPrefix);
                new PermissionGroupController(permissionGroupRepository, logService, nodePermissionIndex).register(cfg.routes, apiPrefix);
            }
            DaemonHttpProxy httpProxy = new DaemonHttpProxy();
            new FileController(nodeService, httpProxy, logService).register(cfg.routes, apiPrefix);
            new TerminalController(nodeService, terminalTicketStore).register(cfg.routes, apiPrefix);
            new PackageController(nodeService, httpProxy, logService).register(cfg.routes, apiPrefix);

            
            socketIOServer.register(cfg.routes);

            
            new PageController(renderer, singleUserMode).register(cfg.routes);
            cfg.routes.get("/static/*", this::serveStatic);

            
            cfg.routes.get(apiPrefix + "/addons/menus",
                    ctx -> ctx.json(ApiResponse.ok(addonMenuRegistry.list())));
            registerAddonProxies(cfg.routes);
            if (addonManager != null) {
                new im.xz.cn.lingconsole.app.panel.controller.AddonsController(addonManager)
                        .register(cfg.routes, apiPrefix);
                registerAddonDispatchers(cfg.routes);
            }

            
            cfg.routes.exception(ApiException.class, (e, ctx) -> {
                ctx.status(e.getStatus());
                ctx.json(ApiResponse.error(e.getStatus(), e.getMessage()));
            });
            cfg.routes.exception(PageRedirectException.class, (e, ctx) -> {
                
            });
            cfg.routes.exception(Exception.class, (e, ctx) -> {
                log.error("Panel 请求错误: {} {}", ctx.method(), ctx.path(), e);
                ctx.status(500);
                ctx.json(ApiResponse.error(500, im.xz.cn.lingconsole.common.util.ErrorMessageUtil.friendly(e)));
            });
        });

        javalin.start(config.host(), config.port());
        log.info("Panel 已启动: http://{}:{}", config.host(), config.port());

        
        connectLocalDaemon();
        if (nodePermissionIndex != null) {
            nodePermissionIndex.start();
        }
    }

    public void stop() {
        nodeService.stop();
        if (socketIOServer != null) {
            socketIOServer.stop();
        }
        if (javalin != null) {
            javalin.stop();
        }
    }

    private void initServices() {
        UserRepository userRepository = new UserRepository(db);
        SessionRepository sessionRepository = new SessionRepository(db);
        NodeRepository nodeRepository = new NodeRepository(db);
        LogRepository logRepository = new LogRepository(db);
        RootAccountRepository rootAccountRepository = new RootAccountRepository(db);
        permissionGroupRepository = new PermissionGroupRepository(db);
        userGroupRepository = new UserGroupRepository(db, permissionGroupRepository);

        userService = new UserService(userRepository, rootAccountRepository);
        userService.setSingleUserMode(singleUserMode);
        sessionService = new SessionService(sessionRepository, userService);
        permissionService = new PermissionService(userRepository, userGroupRepository);
        userService.setPermissionService(permissionService);
        logService = new LogService(logRepository);
        nodeService = new NodeService(nodeRepository);
        nodeService.start();
        nodePermissionIndex = new im.xz.cn.lingconsole.app.panel.service.NodePermissionIndex(nodeRepository, nodeService);
        loginAttemptService = new LoginAttemptService(config.maxLoginAttempts(), config.lockoutDuration(), config.rateLimitPerSecond());
    }

    private void connectLocalDaemon() {
        try {
            Node node = nodeService.ensureLocalDaemon(
                    daemonConfig.name(),
                    "ws://127.0.0.1:" + daemonConfig.port(),
                    daemonConfig.key());
            log.info("本地 Daemon 节点: {} ({})", node.getName(), node.getUrl());
        } catch (Exception e) {
            log.warn("本地 Daemon 自动连接失败: {}", e.getMessage());
        }
    }

    
    
    

    private void setupSocketEvents() {
        socketIOServer.registerNamespace(Constants.PANEL_NS);

        socketIOServer.onConnect(Constants.PANEL_NS, (conn, query) -> {
            im.xz.cn.lingconsole.app.panel.model.AuthUser auth = resolveAuth(conn);
            if (auth == null) {
                conn.emit("auth", Map.of("status", 401, "message", "认证失败"));
                conn.close();
                return;
            }
            panelSessions.put(conn.sessionId(), auth);
            conn.emit("auth", Map.of(
                    "status", 200,
                    "message", "success",
                    "data", Map.of("sid", conn.sessionId(), "user", auth.user().getUsername())));
            log.info("Panel Socket 已连接: sid={}, user={}", conn.sessionId(), auth.user().getUsername());
        });

        socketIOServer.on(Constants.PANEL_NS, "dashboard:stats", (conn, event, data) -> {
            im.xz.cn.lingconsole.app.panel.model.AuthUser auth = panelSessions.get(conn.sessionId());
            if (auth == null) {
                conn.emit("dashboard:stats", SocketIOResponse.error(data, 401, "未认证"));
                return;
            }
            
            boolean includeSystem = auth.hasPermission(im.xz.cn.lingconsole.common.permission.Permissions.SYSTEM_STATUS);
            conn.emit("dashboard:stats", SocketIOResponse.ok(data, dashboardStats(includeSystem)));
        });

        socketIOServer.on(Constants.PANEL_NS, "terminal:connect", this::handleTerminalConnect);
        socketIOServer.on(Constants.PANEL_NS, "terminal:input", this::handleTerminalInput);
        socketIOServer.on(Constants.PANEL_NS, "terminal:resize", this::handleTerminalResize);
        socketIOServer.on(Constants.PANEL_NS, "terminal:close", this::handleTerminalClose);
        socketIOServer.onDisconnect(this::handlePanelDisconnect);
    }

    private im.xz.cn.lingconsole.app.panel.model.AuthUser resolveAuth(im.xz.cn.lingconsole.common.socketio.SocketIOConnection conn) {
        String token = conn.cookie(AuthMiddleware.COOKIE_NAME);
        if (token == null || token.isBlank()) {
            return null;
        }
        User user = sessionService.validateToken(token);
        if (user == null) {
            return null;
        }
        return permissionService.buildAuthUser(user);
    }

    
    private void handleTerminalConnect(im.xz.cn.lingconsole.common.socketio.SocketIOConnection conn,
                                       String event, Object data) {
        im.xz.cn.lingconsole.app.panel.model.AuthUser auth = panelSessions.get(conn.sessionId());
        if (auth == null) {
            conn.emit(event, SocketIOResponse.error(data, 401, "未认证"));
            return;
        }
        String ticket = null;
        int cols = 80;
        int rows = 24;
        if (data instanceof com.fasterxml.jackson.databind.JsonNode n) {
            ticket = n.path("ticket").asText(null);
            cols = n.path("cols").asInt(80);
            rows = n.path("rows").asInt(24);
        }
        im.xz.cn.lingconsole.app.panel.service.TerminalTicketStore.Ticket t =
                terminalTicketStore.consume(ticket, auth.user().getId());
        if (t == null) {
            conn.emit(event, SocketIOResponse.error(data, 403, "票据无效或已过期"));
            return;
        }
        try {
            Node node = nodeService.findById(t.nodeId()).orElse(null);
            if (node == null) {
                conn.emit(event, SocketIOResponse.error(data, 404, "节点不存在"));
                return;
            }
            DaemonConnection dconn = nodeService.getConnection(t.nodeId());
            if (dconn == null) {
                conn.emit(event, SocketIOResponse.error(data, 400, "节点离线或未连接, 请确保 55700 端口可被访问"));
                return;
            }
            Object resp = dconn.requestBlocking("passport:register",
                    Map.of("appId", t.appId() == null ? "" : t.appId(), "cols", cols, "rows", rows), 8000);
            if (!(resp instanceof com.fasterxml.jackson.databind.JsonNode n)
                    || n.path("status").asInt(-1) != 200) {
                String msg = resp instanceof com.fasterxml.jackson.databind.JsonNode jn
                        ? jn.path("message").asText("票据注册失败") : "票据注册失败";
                conn.emit(event, SocketIOResponse.error(data, 400, msg));
                return;
            }
            String passport = n.path("data").path("passport").asText();
            if (passport.isBlank()) {
                conn.emit(event, SocketIOResponse.error(data, 400, "票据注册失败"));
                return;
            }
            im.xz.cn.lingconsole.common.socketio.SocketIOClient stream = new im.xz.cn.lingconsole.common.socketio.SocketIOClient(
                    node.getUrl(), "/stream?passport=" + java.net.URLEncoder.encode(passport, StandardCharsets.UTF_8));
            if (!stream.connect()) {
                conn.emit(event, SocketIOResponse.error(data, 502, "无法连接终端流, 请确保 55700 端口可被访问"));
                return;
            }
            TerminalBridge bridge = new TerminalBridge(stream);
            bridge.bind(conn);
            TerminalBridge previous = terminalBridges.put(conn.sessionId(), bridge);
            if (previous != null) {
                previous.close();
            }
            conn.emit(event, SocketIOResponse.ok(data, Map.of(
                    "status", 200,
                    "terminalId", n.path("data").path("terminalId").asText(""))));
        } catch (Exception e) {
            log.warn("终端连接失败: {}", e.getMessage());
            conn.emit(event, SocketIOResponse.error(data, 500, im.xz.cn.lingconsole.common.util.ErrorMessageUtil.friendly(e)));
        }
    }

    private void handleTerminalInput(im.xz.cn.lingconsole.common.socketio.SocketIOConnection conn,
                                     String event, Object data) {
        TerminalBridge bridge = terminalBridges.get(conn.sessionId());
        if (bridge != null) {
            bridge.input(data);
        }
    }

    private void handleTerminalResize(im.xz.cn.lingconsole.common.socketio.SocketIOConnection conn,
                                      String event, Object data) {
        TerminalBridge bridge = terminalBridges.get(conn.sessionId());
        if (bridge != null) {
            bridge.resize(data);
        }
    }

    private void handleTerminalClose(im.xz.cn.lingconsole.common.socketio.SocketIOConnection conn,
                                     String event, Object data) {
        TerminalBridge bridge = terminalBridges.remove(conn.sessionId());
        if (bridge != null) {
            bridge.close();
        }
    }

    private void handlePanelDisconnect(String sessionId) {
        panelSessions.remove(sessionId);
        TerminalBridge bridge = terminalBridges.remove(sessionId);
        if (bridge != null) {
            bridge.close();
        }
    }

    
    private static final class TerminalBridge {
        final im.xz.cn.lingconsole.common.socketio.SocketIOClient stream;

        TerminalBridge(im.xz.cn.lingconsole.common.socketio.SocketIOClient stream) {
            this.stream = stream;
        }

        void bind(im.xz.cn.lingconsole.common.socketio.SocketIOConnection conn) {
            stream.on("terminal:output", (c, e, d) -> conn.emit("terminal:output", d));
            stream.on("terminal:status", (c, e, d) -> conn.emit("terminal:status", d));
            stream.on("terminal:exit", (c, e, d) -> conn.emit("terminal:exit", d));
            stream.on("auth", (c, e, d) -> conn.emit("terminal:auth", d));
        }

        void input(Object data) {
            stream.emit("terminal:input", data);
        }

        void resize(Object data) {
            stream.emit("terminal:resize", data);
        }

        void close() {
            stream.disconnect();
        }
    }

    private Map<String, Object> dashboardStats(boolean includeSystem) {
        List<Node> nodes = nodeService.list();
        long online = nodes.stream().filter(n -> n.getStatus() == Node.STATUS_ONLINE).count();
        if (!includeSystem) {
            return Map.of(
                    "nodeCount", nodes.size(),
                    "nodeOnline", online);
        }
        Object systemInfo = null;
        Node onlineNode = nodes.stream().filter(n -> n.getStatus() == Node.STATUS_ONLINE).findFirst().orElse(null);
        if (onlineNode != null) {
            try {
                systemInfo = nodeService.systemInfo(onlineNode.getId());
            } catch (Exception e) {
                
            }
        }
        if (systemInfo == null) {
            systemInfo = SystemInfo.collect();
        }
        return Map.of(
                "systemInfo", systemInfo,
                "nodeCount", nodes.size(),
                "nodeOnline", online);
    }

    







    private void serveStatic(Context ctx) {
        String path = ctx.path();
        String resource = path.substring("/static/".length());

        
        Path staticRoot = Path.of(webDir, "static").toAbsolutePath().normalize();
        Path external = staticRoot.resolve(resource).normalize();
        if (resource.contains("..") || resource.contains("\\") || resource.startsWith("/")
                || !external.startsWith(staticRoot)) {
            ctx.status(404);
            ctx.result("Not Found");
            return;
        }
        if (Files.exists(external) && Files.isRegularFile(external)) {
            try {
                ctx.contentType(mimeType(resource));
                ctx.result(Files.readAllBytes(external));
                return;
            } catch (Exception e) {
                log.warn("读取外部静态资源失败: {}", external, e);
            }
        }

        
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("static/" + resource)) {
            if (in != null) {
                ctx.contentType(mimeType(resource));
                ctx.result(in.readAllBytes());
                return;
            }
        } catch (Exception e) {
            log.warn("读取内置静态资源失败: {}", resource, e);
        }

        ctx.status(404);
        ctx.result("Not Found");
    }

    private String mimeType(String resource) {
        if (resource.endsWith(".css")) return "text/css; charset=utf-8";
        if (resource.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (resource.endsWith(".html")) return "text/html; charset=utf-8";
        if (resource.endsWith(".json")) return "application/json; charset=utf-8";
        if (resource.endsWith(".svg")) return "image/svg+xml";
        if (resource.endsWith(".png")) return "image/png";
        if (resource.endsWith(".ico")) return "image/x-icon";
        if (resource.endsWith(".woff2")) return "font/woff2";
        if (resource.endsWith(".map")) return "application/json";
        return "application/octet-stream";
    }

    
    
    

    private void registerAddonDispatchers(io.javalin.config.RoutesConfig routes) {
        String prefix = Constants.WEB_API_PREFIX + "/addon/*";
        routes.get(prefix, this::dispatchAddon);
        routes.post(prefix, this::dispatchAddon);
        routes.put(prefix, this::dispatchAddon);
        routes.delete(prefix, this::dispatchAddon);
        routes.patch(prefix, this::dispatchAddon);
    }

    
    private void dispatchAddon(Context ctx) {
        String path = ctx.path();
        String rest = path.substring(Constants.WEB_API_PREFIX.length() + "/addon/".length());
        int slash = rest.indexOf('/');
        String name = slash == -1 ? rest : rest.substring(0, slash);
        String sub = slash == -1 ? "/" : rest.substring(slash);
        var mgr = addonManager;
        if (mgr == null) {
            notFound(ctx);
            return;
        }
        var context = mgr.contextOf(name);
        if (context == null) {
            notFound(ctx);
            return;
        }
        String permission = context.panelRoutePermission(ctx.method().name(), sub);
        if (!hasAddonPermission(ctx, permission)) {
            ctx.status(403);
            ctx.json(ApiResponse.error(403, "权限不足"));
            return;
        }
        im.xz.cn.lingconsole.addon.AddonRouteHandler handler = context.panelRouteHandler(ctx.method().name(), sub);
        if (handler == null) {
            notFound(ctx);
            return;
        }
        handler.handle(ctx);
    }

    private boolean hasAddonPermission(Context ctx, String requiredPermission) {
        String perm = requiredPermission == null
                ? im.xz.cn.lingconsole.common.permission.Permissions.PERMISSION_ASSIGN
                : requiredPermission;
        if (im.xz.cn.lingconsole.addon.AddonContext.PUBLIC.equals(perm)) {
            return true;
        }
        var auth = im.xz.cn.lingconsole.app.panel.middleware.AuthMiddleware.authUser(ctx);
        return auth != null && auth.hasPermission(perm);
    }

    private void notFound(Context ctx) {
        ctx.status(404);
        ctx.json(ApiResponse.error(404, "Not Found"));
    }

    
    
    

    private void registerAddonProxies(io.javalin.config.RoutesConfig routes) {
        if (addonProxyRegistry == null) {
            return;
        }
        for (im.xz.cn.lingconsole.common.addon.AddonProxy cfg : addonProxyRegistry.list()) {
            String prefix = Constants.WEB_API_PREFIX + "/addon/" + cfg.addonName() + cfg.mountPath() + "/*";
            io.javalin.http.Handler dispatch = ctx -> {
                if (!hasAddonPermission(ctx, cfg.requiredPermission())) {
                    ctx.status(403);
                    ctx.json(ApiResponse.error(403, "权限不足"));
                    return;
                }
                im.xz.cn.lingconsole.app.addon.ReverseProxy.forward(ctx, cfg);
            };
            routes.get(prefix, dispatch);
            routes.post(prefix, dispatch);
            routes.put(prefix, dispatch);
            routes.delete(prefix, dispatch);
            routes.patch(prefix, dispatch);
            log.info("插件代理已挂载: {} -> {}://{}:{}{}", prefix, cfg.scheme(), cfg.host(), cfg.port(), cfg.basePath());
        }
    }

    
    public static final class PageRedirectException extends RuntimeException {
    }
}
