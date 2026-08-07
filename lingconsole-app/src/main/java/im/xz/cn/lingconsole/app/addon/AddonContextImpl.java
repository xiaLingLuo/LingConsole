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
package im.xz.cn.lingconsole.app.addon;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import im.xz.cn.lingconsole.addon.AddonContext;
import im.xz.cn.lingconsole.addon.AddonInfo;
import im.xz.cn.lingconsole.addon.AddonLogger;
import im.xz.cn.lingconsole.addon.AddonRouteHandler;
import im.xz.cn.lingconsole.addon.AddonRouteMethod;
import im.xz.cn.lingconsole.addon.AddonSocketHandler;
import im.xz.cn.lingconsole.addon.CommandHandler;
import im.xz.cn.lingconsole.addon.ConfigEntry;
import im.xz.cn.lingconsole.addon.ConfigType;
import im.xz.cn.lingconsole.app.panel.PanelConfig;
import im.xz.cn.lingconsole.app.panel.model.Node;
import im.xz.cn.lingconsole.app.panel.remote.DaemonConnection;
import im.xz.cn.lingconsole.app.panel.remote.DaemonHttpProxy;
import im.xz.cn.lingconsole.app.panel.service.LogService;
import im.xz.cn.lingconsole.app.panel.service.NodeService;
import im.xz.cn.lingconsole.app.panel.service.UserService;
import im.xz.cn.lingconsole.common.addon.AddonSocketRegistry;
import im.xz.cn.lingconsole.common.command.ConsoleCommandDispatcher;
import im.xz.cn.lingconsole.daemon.DaemonConfig;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class AddonContextImpl implements AddonContext, AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AddonInfo info;
    private final AddonLogger logger;
    private final im.xz.cn.lingconsole.common.addon.AddonManager addonManager;
    private final NodeService nodeService;
    private final UserService userService;
    private final LogService logService;
    private final PanelConfig panelConfig;
    private final DaemonConfig daemonConfig;
    private final im.xz.cn.lingconsole.app.panel.repository.DatabaseManager db;
    private final Path dataDir;
    private final Path addonDataDir;
    private final DaemonHttpProxy proxy = new DaemonHttpProxy();
    private final AddonSocketRegistry socketRegistry;
    private final im.xz.cn.lingconsole.common.addon.AddonMenuRegistry menuRegistry;
    private final im.xz.cn.lingconsole.common.addon.AddonProxyRegistry proxyRegistry;
    private final ConsoleCommandDispatcher commandDispatcher;
    private final Map<String, RouteEntry> panelRoutes = new ConcurrentHashMap<>();
    private final Map<String, RouteEntry> daemonRoutes = new ConcurrentHashMap<>();

    private record RouteEntry(AddonRouteHandler handler, String permission) {
    }

    private final ScheduledExecutorService scheduler;
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
    private final ConfigAdapter config = new ConfigAdapter();

    public AddonContextImpl(AddonInfo info, AddonLogger logger,
                            NodeService nodeService, UserService userService, LogService logService,
                            PanelConfig panelConfig, DaemonConfig daemonConfig, Path dataDir,
                            AddonSocketRegistry socketRegistry,
                            im.xz.cn.lingconsole.common.addon.AddonMenuRegistry menuRegistry,
                            im.xz.cn.lingconsole.common.addon.AddonProxyRegistry proxyRegistry,
                            im.xz.cn.lingconsole.app.panel.repository.DatabaseManager db,
                            ConsoleCommandDispatcher commandDispatcher,
                            im.xz.cn.lingconsole.common.addon.AddonManager addonManager) {
        this.info = info;
        this.logger = logger;
        this.addonManager = addonManager;
        this.nodeService = nodeService;
        this.userService = userService;
        this.logService = logService;
        this.panelConfig = panelConfig;
        this.daemonConfig = daemonConfig;
        this.dataDir = dataDir;
        this.socketRegistry = socketRegistry;
        this.menuRegistry = menuRegistry;
        this.proxyRegistry = proxyRegistry;
        this.db = db;
        this.commandDispatcher = commandDispatcher;
        this.addonDataDir = dataDir.resolve("addons").resolve(info.name());
        try {
            Files.createDirectories(addonDataDir);
        } catch (Exception e) {
            logger.warn("创建插件数据目录失败: {}", addonDataDir, e);
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "addon-" + info.name() + "-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public AddonInfo info() {
        ensureOpen();
        return info;
    }

    @Override
    public AddonLogger logger() {
        ensureOpen();
        return logger;
    }

    
    
    

    @Override
    public im.xz.cn.lingconsole.addon.service.NodeService nodes() {
        ensureOpen();
        return new NodeAdapter();
    }

    @Override
    public im.xz.cn.lingconsole.addon.service.AppService apps() {
        ensureOpen();
        return new AppAdapter();
    }

    @Override
    public im.xz.cn.lingconsole.addon.service.FileService files() {
        ensureOpen();
        return new FileAdapter();
    }

    @Override
    public im.xz.cn.lingconsole.addon.service.MonitorService monitor() {
        ensureOpen();
        return new MonitorAdapter();
    }

    @Override
    public im.xz.cn.lingconsole.addon.service.ExecService exec() {
        ensureOpen();
        return new ExecAdapter();
    }

    @Override
    public im.xz.cn.lingconsole.addon.service.DataService data() {
        ensureOpen();
        return new DataAdapter();
    }

    @Override
    public im.xz.cn.lingconsole.addon.service.UserService users() {
        ensureOpen();
        return new UserAdapter();
    }

    @Override
    public im.xz.cn.lingconsole.addon.service.LogService logs() {
        ensureOpen();
        return new LogAdapter();
    }

    @Override
    public im.xz.cn.lingconsole.addon.service.ConfigService config() {
        ensureOpen();
        return config;
    }

    
    
    

    @Override
    public void registerPanelRoute(AddonRouteMethod method, String path, AddonRouteHandler handler) {
        registerPanelRoute(method, path, handler, null);
    }

    @Override
    public void registerPanelRoute(AddonRouteMethod method, String path, AddonRouteHandler handler,
                                   String requiredPermission) {
        ensureOpen();
        if (handler != null) {
            panelRoutes.put(key(method.name(), path), new RouteEntry(handler, normalizePermission(requiredPermission)));
        }
    }

    
    private static final java.util.regex.Pattern PERMISSION_KEY_PATTERN =
            java.util.regex.Pattern.compile("^[a-z0-9]+(\\.[a-z0-9]+)*$");

    
    private String sanitizePermissionKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(java.util.Locale.ROOT);
        if (!PERMISSION_KEY_PATTERN.matcher(normalized).matches()) {
            if (addonManager != null) {
                addonManager.markError(info.name(),
                        "非法权限节点 \"" + key + "\"");
            }
            return null;
        }
        return normalized;
    }

    private String normalizePermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return null;
        }
        String trimmed = permission.trim();
        if (AddonContext.PUBLIC.equals(trimmed)) {
            return trimmed;
        }
        if (trimmed.startsWith("lingconsole.")) {
            return trimmed;
        }
        String normalized = sanitizePermissionKey(trimmed);
        if (normalized == null) {
            return null;
        }
        String addonPrefix = info.name().toLowerCase(java.util.Locale.ROOT) + ".";
        if (normalized.startsWith(addonPrefix)) {
            String relative = normalized.substring(addonPrefix.length());
            im.xz.cn.lingconsole.common.permission.PluginPermissionRegistry.register(
                    info.name(), relative, relative);
            return normalized;
        }
        String full = info.name() + "." + normalized;
        im.xz.cn.lingconsole.common.permission.PluginPermissionRegistry.register(info.name(), normalized, normalized);
        return full;
    }

    @Override
    public void registerPermission(String key, String label) {
        ensureOpen();
        String normalized = sanitizePermissionKey(key);
        if (normalized == null) {
            return;
        }
        im.xz.cn.lingconsole.common.permission.PluginPermissionRegistry.register(info.name(), normalized, label);
        logger.info("已注册权限: {}:{} ({})", info.name(), normalized, label);
    }

    @Override
    public void registerDaemonRoute(AddonRouteMethod method, String path, AddonRouteHandler handler) {
        ensureOpen();
        if (handler != null) {
            daemonRoutes.put(key(method.name(), path), new RouteEntry(handler, null));
        }
    }

    @Override
    public void registerCommand(String command, CommandHandler handler) {
        ensureOpen();
        if (commandDispatcher == null || handler == null) {
            return;
        }
        boolean ok = commandDispatcher.register(info.name(), command, handler);
        if (ok) {
            logger.info("已注册控制台指令: {}:{}", info.name(), command);
        } else {
            logger.warn("注册控制台指令失败: {}:{}", info.name(), command);
        }
    }

    @Override
    public void registerSocketEvent(String namespace, String event, String requiredPermission,
                                    AddonSocketHandler handler) {
        ensureOpen();
        String normalizedPermission = normalizePermission(requiredPermission);
        if (normalizedPermission == null) {
            throw new IllegalArgumentException("Socket 事件必须声明有效的 requiredPermission");
        }
        socketRegistry.register(info.name(), namespace, event, normalizedPermission, handler);
    }

    @Override
    public void registerPanelMenu(String label, String url) {
        ensureOpen();
        menuRegistry.register(info.name(), label, url);
    }

    @Override
    public void registerPanelProxy(String mountPath, String scheme, String host, int port, String basePath) {
        registerPanelProxy(mountPath, scheme, host, port, basePath, (String) null);
    }

    @Override
    public void registerPanelProxy(String mountPath, String scheme, String host, int port, String basePath,
                                   String requiredPermission) {
        registerPanelProxy(mountPath, scheme, host, port, basePath, requiredPermission, null);
    }

    @Override
    public void registerPanelProxy(String mountPath, String scheme, String host, int port, String basePath,
                                   String requiredPermission, java.util.Set<String> forwardHeaders) {
        ensureOpen();
        proxyRegistry.register(info.name(), mountPath, scheme, host, port, basePath, requiredPermission, forwardHeaders);
    }

    @Override
    public ScheduledExecutorService scheduler() {
        ensureOpen();
        return scheduler;
    }

    @Override
    public Path dataDir() {
        ensureOpen();
        return dataDir;
    }

    @Override
    public Path addonDataDir() {
        ensureOpen();
        return addonDataDir;
    }

    
    public AddonRouteHandler panelRoute(String method, String path) {
        ensureOpen();
        RouteEntry e = panelRoutes.get(key(method, path));
        return e == null ? null : e.handler();
    }

    
    public AddonRouteHandler daemonRoute(String method, String path) {
        ensureOpen();
        RouteEntry e = daemonRoutes.get(key(method, path));
        return e == null ? null : e.handler();
    }

    
    public String panelRoutePermission(String method, String path) {
        ensureOpen();
        RouteEntry e = panelRoutes.get(key(method, path));
        return e == null ? null : e.permission();
    }

    @Override
    public AddonRouteHandler panelRouteHandler(String method, String path) {
        return panelRoute(method, path);
    }

    @Override
    public AddonRouteHandler daemonRouteHandler(String method, String path) {
        return daemonRoute(method, path);
    }

    
    public void saveConfig(Map<String, String> values) {
        ensureOpen();
        config.save(values);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        panelRoutes.clear();
        daemonRoutes.clear();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("插件 Context 已关闭: " + info.name());
        }
    }

    private static String key(String method, String path) {
        return method.toUpperCase() + " " + (path == null ? "/" : path);
    }

    
    
    

    private Node findNode(String nodeId) {
        ensureOpen();
        if (nodeService == null) {
            return null;
        }
        return nodeService.findById(nodeId).orElse(null);
    }

    private class NodeAdapter implements im.xz.cn.lingconsole.addon.service.NodeService {
        @Override
        public List<Map<String, Object>> listNodes() {
            ensureOpen();
            if (nodeService == null) {
                return List.of();
            }
            return nodeService.list().stream().map(AddonContextImpl::nodeToMap).toList();
        }

        @Override
        public Map<String, Object> getNode(String id) {
            ensureOpen();
            if (nodeService == null) {
                return null;
            }
            Node node = findNode(id);
            return node == null ? null : nodeToMap(node);
        }

        @Override
        public int nodeStatus(String id) {
            ensureOpen();
            if (nodeService == null) {
                return -1;
            }
            Node node = findNode(id);
            return node == null ? -1 : node.getStatus();
        }
    }

    private class AppAdapter implements im.xz.cn.lingconsole.addon.service.AppService {
        @Override
        public List<Map<String, Object>> listApps(String nodeId) {
            ensureOpen();
            JsonNode data = request(nodeId, "app:list", Map.of());
            return jsonArrayToMaps(data);
        }

        @Override
        public Map<String, Object> createApp(String nodeId, String name, String command,
                                             List<String> args, String workDir) {
            ensureOpen();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", im.xz.cn.lingconsole.common.util.IdUtil.uuid().replace("-", "").toLowerCase().substring(0, 12));
            payload.put("name", name);
            payload.put("command", command);
            payload.put("type", "general");
            payload.put("autoStart", false);
            payload.put("args", args == null ? List.of() : args);
            JsonNode created = request(nodeId, "app:create", payload);
            if (created == null || created.isMissingNode() || !created.has("id")) {
                return null;
            }
            String appId = created.path("id").asText();
            if (workDir != null && !workDir.isBlank()) {
                request(nodeId, "app:update", Map.of("id", appId, "workDir", workDir));
            }
            return MAPPER.convertValue(created, new TypeReference<>() {
            });
        }

        @Override
        public boolean startApp(String nodeId, String appId) {
            ensureOpen();
            return isOk(request(nodeId, "app:start", Map.of("id", appId)));
        }

        @Override
        public boolean stopApp(String nodeId, String appId) {
            ensureOpen();
            return isOk(request(nodeId, "app:stop", Map.of("id", appId)));
        }

        @Override
        public boolean restartApp(String nodeId, String appId) {
            ensureOpen();
            return isOk(request(nodeId, "app:restart", Map.of("id", appId)));
        }

        @Override
        public List<String> appLogs(String nodeId, String appId, int count) {
            ensureOpen();
            JsonNode data = request(nodeId, "app:log", Map.of("id", appId, "count", count));
            List<String> logs = new ArrayList<>();
            if (data != null && data.path("logs").isArray()) {
                data.path("logs").forEach(n -> logs.add(n.asText()));
            }
            return logs;
        }

        @Override
        public boolean signalApp(String nodeId, String appId, String signal) {
            ensureOpen();
            Node node = findNode(nodeId);
            if (node == null) {
                return false;
            }
            try {
                String body = "{\"signal\":" + MAPPER.writeValueAsString(signal) + "}";
                HttpResponse<String> resp = proxy.postJson(node, "/apps/" + appId + "/signal", null, body);
                return MAPPER.readTree(resp.body()).path("status").asInt() == 200;
            } catch (Exception e) {
                logger.debug("app signal 失败", e);
                return false;
            }
        }
    }

    private class FileAdapter implements im.xz.cn.lingconsole.addon.service.FileService {
        @Override
        public List<Map<String, Object>> listFiles(String nodeId, String path) {
            ensureOpen();
            Node node = findNode(nodeId);
            if (node == null) return List.of();
            try {
                HttpResponse<String> resp = proxy.get(node, "/files/list", Map.of("path", path));
                return jsonArrayToMaps(MAPPER.readTree(resp.body()).path("data"));
            } catch (Exception e) {
                logger.debug("files.list 失败", e);
                return List.of();
            }
        }

        @Override
        public String readFile(String nodeId, String path) {
            ensureOpen();
            Node node = findNode(nodeId);
            if (node == null) return null;
            try {
                HttpResponse<String> resp = proxy.get(node, "/files/read", Map.of("path", path));
                return MAPPER.readTree(resp.body()).path("data").path("content").asText(null);
            } catch (Exception e) {
                logger.debug("files.read 失败", e);
                return null;
            }
        }

        @Override
        public boolean writeFile(String nodeId, String path, String content) {
            ensureOpen();
            Node node = findNode(nodeId);
            if (node == null) return false;
            try {
                String body = "{\"path\":" + MAPPER.writeValueAsString(path)
                        + ",\"content\":" + MAPPER.writeValueAsString(content == null ? "" : content) + "}";
                HttpResponse<String> resp = proxy.postJson(node, "/files/write", null, body);
                return MAPPER.readTree(resp.body()).path("status").asInt() == 200;
            } catch (Exception e) {
                logger.debug("files.write 失败", e);
                return false;
            }
        }

        @Override
        public boolean deleteFile(String nodeId, String path) {
            ensureOpen();
            Node node = findNode(nodeId);
            if (node == null) return false;
            try {
                HttpResponse<String> resp = proxy.delete(node, "/files", Map.of("path", path));
                return MAPPER.readTree(resp.body()).path("status").asInt() == 200;
            } catch (Exception e) {
                logger.debug("files.delete 失败", e);
                return false;
            }
        }

        @Override
        public boolean createDirectory(String nodeId, String path) {
            ensureOpen();
            Node node = findNode(nodeId);
            if (node == null) return false;
            try {
                HttpResponse<String> resp = proxy.postJson(node, "/files/mkdir", Map.of("path", path), null);
                return MAPPER.readTree(resp.body()).path("status").asInt() == 200;
            } catch (Exception e) {
                logger.debug("files.mkdir 失败", e);
                return false;
            }
        }
    }

    private class MonitorAdapter implements im.xz.cn.lingconsole.addon.service.MonitorService {
        @Override
        public Map<String, Object> snapshot(String nodeId) {
            ensureOpen();
            JsonNode data = request(nodeId, "monitor:stats", Map.of());
            return data == null ? Map.of() : MAPPER.convertValue(data, new TypeReference<>() {
            });
        }
    }

    private class ExecAdapter implements im.xz.cn.lingconsole.addon.service.ExecService {
        @Override
        public im.xz.cn.lingconsole.addon.ExecResult exec(String nodeId, String command, long timeoutMs) {
            ensureOpen();
            Node node = findNode(nodeId);
            if (node == null) {
                return new im.xz.cn.lingconsole.addon.ExecResult(-1, "", "节点不存在", false);
            }
            try {
                String body = "{\"command\":" + MAPPER.writeValueAsString(command)
                        + ",\"timeoutMs\":" + timeoutMs + "}";
                HttpResponse<String> resp = proxy.postJson(node, "/exec", null, body);
                JsonNode data = MAPPER.readTree(resp.body()).path("data");
                return new im.xz.cn.lingconsole.addon.ExecResult(
                        data.path("exitCode").asInt(-1),
                        data.path("stdout").asText(""),
                        data.path("stderr").asText(""),
                        data.path("timedOut").asBoolean(false));
            } catch (Exception e) {
                logger.debug("exec 失败", e);
                return new im.xz.cn.lingconsole.addon.ExecResult(-1, "", String.valueOf(e.getMessage()), false);
            }
        }
    }

    private class DataAdapter implements im.xz.cn.lingconsole.addon.service.DataService {
        @Override
        public void put(String key, String value) {
            ensureOpen();
            try (java.sql.Connection conn = db.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO addon_data (addon, k, v) VALUES (?, ?, ?)")) {
                ps.setString(1, info.name());
                ps.setString(2, key);
                ps.setString(3, value);
                ps.executeUpdate();
            } catch (Exception e) {
                logger.debug("addon_data 写入失败", e);
            }
        }

        @Override
        public String get(String key) {
            ensureOpen();
            try (java.sql.Connection conn = db.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                         "SELECT v FROM addon_data WHERE addon = ? AND k = ?")) {
                ps.setString(1, info.name());
                ps.setString(2, key);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            } catch (Exception e) {
                logger.debug("addon_data 读取失败", e);
                return null;
            }
        }

        @Override
        public void delete(String key) {
            ensureOpen();
            try (java.sql.Connection conn = db.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM addon_data WHERE addon = ? AND k = ?")) {
                ps.setString(1, info.name());
                ps.setString(2, key);
                ps.executeUpdate();
            } catch (Exception e) {
                logger.debug("addon_data 删除失败", e);
            }
        }

        @Override
        public Map<String, String> all() {
            ensureOpen();
            Map<String, String> map = new LinkedHashMap<>();
            try (java.sql.Connection conn = db.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                         "SELECT k, v FROM addon_data WHERE addon = ?")) {
                ps.setString(1, info.name());
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        map.put(rs.getString(1), rs.getString(2));
                    }
                }
            } catch (Exception e) {
                logger.debug("addon_data 列表失败", e);
            }
            return map;
        }
    }

    private class UserAdapter implements im.xz.cn.lingconsole.addon.service.UserService {
        @Override
        public List<Map<String, Object>> listUsers() {
            ensureOpen();
            if (userService == null) {
                return List.of();
            }
            return userService.listUsers().stream().map(u -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", u.getId());
                m.put("username", u.getUsername());
                m.put("role", u.getRole().value());
                m.put("roleName", u.getRole().roleName());
                return m;
            }).toList();
        }

        @Override
        public Map<String, Object> getUser(String id) {
            ensureOpen();
            if (userService == null) {
                return null;
            }
            var user = userService.findById(id);
            if (user == null) return null;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", user.getId());
            m.put("username", user.getUsername());
            m.put("role", user.getRole().value());
            m.put("roleName", user.getRole().roleName());
            return m;
        }
    }

    private class LogAdapter implements im.xz.cn.lingconsole.addon.service.LogService {
        @Override
        public void record(String action, String target, String detail) {
            ensureOpen();
            if (logService == null) {
                return;
            }
            try {
                logService.recordPlugin(info.name(), action, target, detail, null, null, null);
            } catch (Exception e) {
                logger.debug("日志记录失败", e);
            }
        }
    }

    private class ConfigAdapter implements im.xz.cn.lingconsole.addon.service.ConfigService {
        private final List<ConfigEntry> schema = new ArrayList<>();
        private final Map<String, ConfigEntry> byKey = new LinkedHashMap<>();
        private Map<String, String> values = new LinkedHashMap<>();
        private volatile boolean materialized;

        @Override
        public void define(String key, ConfigType type, String label, String description, String defaultValue) {
            ensureOpen();
            defineEntry(key, type, label, description, defaultValue, null);
        }

        @Override
        public void defineSelect(String key, String label, String description, String defaultValue,
                                 List<String> options) {
            ensureOpen();
            defineEntry(key, ConfigType.SELECT, label, description, defaultValue, options);
        }

        private void defineEntry(String key, ConfigType type, String label, String description,
                                 String defaultValue, List<String> options) {
            if (byKey.containsKey(key)) {
                return;
            }
            ConfigEntry entry = new ConfigEntry(key, type, label, description, defaultValue,
                    options == null ? List.of() : List.copyOf(options), null);
            schema.add(entry);
            byKey.put(key, entry);
        }

        @Override
        public String getString(String key, String def) {
            ensureOpen();
            materialize();
            String v = values.get(key);
            return v != null ? v : def;
        }

        @Override
        public int getInt(String key, int def) {
            ensureOpen();
            String v = getString(key, null);
            if (v == null) return def;
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }

        @Override
        public boolean getBoolean(String key, boolean def) {
            ensureOpen();
            String v = getString(key, null);
            if (v == null) return def;
            return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
        }

        @Override
        public List<ConfigEntry> entries() {
            ensureOpen();
            materialize();
            List<ConfigEntry> out = new ArrayList<>();
            for (ConfigEntry e : schema) {
                out.add(new ConfigEntry(e.key(), e.type(), e.label(), e.description(),
                        e.defaultValue(), e.options(), values.getOrDefault(e.key(), e.defaultValue())));
            }
            return out;
        }

        @Override
        public Map<String, String> values() {
            ensureOpen();
            materialize();
            return Map.copyOf(values);
        }

        public void save(Map<String, String> newValues) {
            ensureOpen();
            materialize();
            Map<String, String> merged = new LinkedHashMap<>(values);
            if (newValues != null) {
                merged.putAll(newValues);
            }
            values = merged;
            writeConfigFile();
        }

        @Override
        public Map<String, Object> panelConfig() {
            ensureOpen();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("host", panelConfig.host());
            m.put("port", panelConfig.port());
            m.put("sessionTimeout", panelConfig.sessionTimeout());
            m.put("maxLoginAttempts", panelConfig.maxLoginAttempts());
            m.put("lockoutDuration", panelConfig.lockoutDuration());
            m.put("rateLimitPerSecond", panelConfig.rateLimitPerSecond());
            m.put("theme", panelConfig.theme());
            m.put("language", panelConfig.language());
            return m;
        }

        @Override
        public Map<String, Object> daemonConfig() {
            ensureOpen();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("host", daemonConfig.host());
            m.put("port", daemonConfig.port());
            m.put("name", daemonConfig.name());
            m.put("whiteListEnabled", daemonConfig.whiteListEnabled());
            m.put("authTimeout", daemonConfig.authTimeout());
            m.put("maxFileTasks", daemonConfig.maxFileTasks());
            m.put("archiveCompressMaxEntries", daemonConfig.archiveCompress().maxEntries());
            m.put("archiveCompressMaxTotalBytes", daemonConfig.archiveCompress().maxTotalBytes());
            m.put("archiveCompressTimeoutSeconds", daemonConfig.archiveCompress().timeoutSeconds());
            m.put("archiveExtractMaxEntries", daemonConfig.archiveExtract().maxEntries());
            m.put("archiveExtractMaxTotalBytes", daemonConfig.archiveExtract().maxTotalBytes());
            m.put("archiveExtractTimeoutSeconds", daemonConfig.archiveExtract().timeoutSeconds());
            m.put("outputBufferSize", daemonConfig.outputBufferSize());
            return m;
        }

        @Override
        public Path dataDir() {
            ensureOpen();
            return dataDir;
        }

        private Path configFile() {
            return addonDataDir.resolve("config.yml");
        }

        private synchronized void materialize() {
            if (materialized) {
                return;
            }
            materialized = true;
            Path file = configFile();
            if (Files.exists(file)) {
                try {
                    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
                    Object loaded = yaml.load(Files.readString(file, StandardCharsets.UTF_8));
                    if (loaded instanceof Map<?, ?> map) {
                        for (var e : map.entrySet()) {
                            values.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                        }
                    }
                } catch (Exception e) {
                    logger.warn("读取插件配置失败: {}", file, e);
                }
            } else {
                
                for (ConfigEntry e : schema) {
                    values.put(e.key(), e.defaultValue());
                }
                writeConfigFile();
            }
        }

        private void writeConfigFile() {
            try {
                Map<String, Object> out = new LinkedHashMap<>();
                for (ConfigEntry e : schema) {
                    out.put(e.key(), values.getOrDefault(e.key(), e.defaultValue()));
                }
                DumperOptions options = new DumperOptions();
                options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
                options.setPrettyFlow(true);
                String yml = new Yaml(options).dump(out);
                Files.writeString(configFile(), yml, StandardCharsets.UTF_8);
            } catch (Exception e) {
                logger.warn("写入插件配置失败: {}", configFile(), e);
            }
        }
    }

    
    
    

    private static Map<String, Object> nodeToMap(Node node) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", node.getId());
        m.put("name", node.getName());
        m.put("url", node.getUrl());
        m.put("status", node.getStatus());
        m.put("style", node.getStyle());
        return m;
    }

    private JsonNode request(String nodeId, String event, Map<String, Object> data) {
        ensureOpen();
        if (nodeService == null) {
            return null;
        }
        DaemonConnection conn = nodeService.getConnection(nodeId);
        if (conn == null) {
            return null;
        }
        try {
            Object resp = conn.requestBlocking(event, data == null ? Map.of() : data, 8000);
            if (resp instanceof JsonNode n && n.path("status").asInt(-1) == 200) {
                return n.path("data");
            }
            return null;
        } catch (Exception e) {
            logger.debug("daemon 请求失败: {}", event, e);
            return null;
        }
    }

    private boolean isOk(JsonNode data) {
        return data != null && !data.isMissingNode();
    }

    private List<Map<String, Object>> jsonArrayToMaps(JsonNode arr) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(n -> list.add(MAPPER.convertValue(n, new TypeReference<>() {
            })));
        }
        return list;
    }
}
