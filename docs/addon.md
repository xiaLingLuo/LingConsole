# 插件 API 文档

本页是插件开发 API 的完整参考。

- [插件开发教程](docs/plugin-guide.md) — 从零写一个插件的完整步骤
- [控制台指令](docs/commands.md) · [WebUI 使用](docs/usage.md) · [安装部署](docs/install.md)

---

## 1. 插件描述 `addon.toml`

JAR **根目录**放置 `addon.toml`：

```toml
name = "myaddon"                # 插件名 (要求唯一)
version = "1.0.0"
main = "com.example.MyAddon"    # 主类
author = "You"
description = "说明"
api-version = "1.1.86"             # API 版本

dependencies = ["other-addon"]        # 可选: 硬依赖
soft-dependencies = ["optional-helper"]  # 可选: 软依赖
```

## 2. 生命周期 `Addon`

```java
public interface Addon {
    default void onLoad(AddonContext ctx) { }    // 加载
    default void onEnable(AddonContext ctx) { }  // 启用
    default void onDisable() { }                 // 停用
}
```

- `onLoad` 抛异常 → 插件进入 `ERROR` 状态（`[ERR]`），不影响宿主与其他插件
- 热重载会完整重建插件

## 3. 上下文 `AddonContext`

路由/服务全部通过 `AddonContext` 访问：

| 方法                                   | 说明                                                                          |
|----------------------------------------|-------------------------------------------------------------------------------|
| `AddonInfo info()`                     | 插件信息（name/version/mainClass/author/description/apiVersion/dependencies） |
| `AddonLogger logger()`                 | 日志（`info/warn/error/debug`，前缀 `[插件名]`）                              |
| `NodeService nodes()`                  | 节点查询服务                                                                  |
| `AppService apps()`                    | 应用管理服务                                                                  |
| `FileService files()`                  | 文件系统服务                                                                  |
| `MonitorService monitor()`             | 节点监控服务                                                                  |
| `ExecService exec()`                   | 远程命令执行服务                                                              |
| `DataService data()`                   | 插件 KV 持久化                                                                |
| `UserService users()`                  | 用户查询服务                                                                  |
| `LogService logs()`                    | 操作审计日志                                                                  |
| `ConfigService config()`               | 标准配置                                                                      |
| `registerPanelRoute(...)`              | 注册面板路由                                                                  |
| `registerDaemonRoute(...)`             | 注册 Daemon 路由                                                              |
| `registerCommand(...)`                 | 注册控制台指令                                                                |
| `registerPermission(key, label)`       | 注册插件权限节点（自动挂到 `<插件名>.<key>`）                                 |
| `registerSocketEvent(...)`             | 注册 Socket.IO 事件                                                           |
| `registerPanelMenu(label, url)`        | 侧栏菜单项                                                                    |
| `registerPanelProxy(...)`              | 反向代理到任意 HTTP 后端                                                      |
| `ScheduledExecutorService scheduler()` | 单线程定时调度器                                                              |
| `Path dataDir()`                       | 全局数据目录 `/lingConsole`                                                   |
| `Path addonDataDir()`                  | 插件私有目录 `addons/<name>/`                                                 |

常量 `AddonContext.PUBLIC = "*"`：路由权限标记，表示**任意已登录用户均可访问**。

## 4. 路由系统

### 4.1 `AddonRouteMethod`

```java
public enum AddonRouteMethod { GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS }
```

### 4.2 `AddonRouteHandler`

```java
@FunctionalInterface
public interface AddonRouteHandler {
    void handle(io.javalin.http.Context ctx);   // Javalin Context
}
```

处理器直接拿到 **Javalin 7 的 `Context`**，

可用：`h.json(obj)`、`h.result(str)`、`h.status(int)`、`h.header(k,v)`、`h.queryParam(name)`、`h.pathParam(name)`、`h.bodyAsClass(Map.class)`、`h.uploadedFile(name)` 等。

### 4.3 面板路由 `registerPanelRoute`

```java
void registerPanelRoute(AddonRouteMethod method, String path, AddonRouteHandler handler);
void registerPanelRoute(AddonRouteMethod method, String path, AddonRouteHandler handler,
                        String requiredPermission);
```

- 挂载路径：`/api/addon/<插件名><path>`
- **默认权限 `permission.assign`**（root（或拥有 permission.assign 的用户））；显式传 `AddonContext.PUBLIC` 放行为任意登录用户
- 自定义权限串自动挂到 `<插件名>.<权限>` 命名空间（插件权限根 = 插件名），并自动注册进权限表供"权限组管理"页预览；也可用 `ctx.registerPermission(key, label)` 显式声明

```java
ctx.registerPanelRoute(AddonRouteMethod.GET, "/info", h -> {
    h.json(Map.of("name", ctx.info().name(), "config", ctx.config().values()));
}, AddonContext.PUBLIC);

ctx.registerPanelRoute(AddonRouteMethod.POST, "/echo",
        h -> h.json(Map.of("received", h.bodyAsClass(Map.class))), AddonContext.PUBLIC);
```

### 4.4 Daemon 路由 `registerDaemonRoute`

```java
void registerDaemonRoute(AddonRouteMethod method, String path, AddonRouteHandler handler);
```

- 挂载路径：`/consoleapi/addon/<插件名><path>`（需 `X-LingConsole-Key` 认证）

```java
ctx.registerDaemonRoute(AddonRouteMethod.GET, "/ping",
        h -> h.json(Map.of("pong", true, "addon", ctx.info().name())));
```

### 4.5 反向代理 `registerPanelProxy`

```java
void registerPanelProxy(String mountPath, String scheme, String host, int port, String basePath);
void registerPanelProxy(String mountPath, String scheme, String host, int port, String basePath,
                        String requiredPermission);
```

把面板路径代理到任意 HTTP 后端（如 phpMyAdmin 的 php-fpm）：

```java
ctx.registerPanelProxy("/pm", "http", "127.0.0.1", 55700, "/consoleapi");
// 访问: /api/addon/<插件名>/pm/...  ->  http://127.0.0.1:55700/consoleapi/...
```

> 反向代理不注入 Daemon Key，如需访问受 Key 保护的 `/consoleapi` 需要用 Daemon 路由或服务接口。

## 5. 控制台指令

```java
void registerCommand(String command, CommandHandler handler);
```

```java
@FunctionalInterface
public interface CommandHandler {
    void execute(String command, String[] args, CommandSender sender);
}

public interface CommandSender {
    void sendMessage(String message);
}
```

```java
ctx.registerCommand("hello", (command, args, sender) ->
        sender.sendMessage("你好, " + ctx.info().name()));
ctx.registerCommand("status", (command, args, sender) ->
        sender.sendMessage("args=" + String.join(",", args)));
```

详见 [控制台指令](commands)。

## 6. Socket 事件

```java
void registerSocketEvent(String namespace, String event, AddonSocketHandler handler);
```

```java
@FunctionalInterface
public interface AddonSocketHandler {
    void handle(AddonSocketConnection connection, String event, Object data);
}
public interface AddonSocketConnection {
    String sessionId();
    void emit(String event, Object data);
    void close();
}
```

```java
ctx.registerSocketEvent("/panel", "addon:hello", (conn, event, data) ->
        conn.emit("addon:hello", Map.of("echo", data, "addon", ctx.info().name())));
```

## 7. 侧栏菜单

```java
void registerPanelMenu(String label, String url);
```

## 8. 标准配置 `ConfigService`

### 8.1 声明配置

```java
void define(String key, ConfigType type, String label, String description, String defaultValue);
void defineSelect(String key, String label, String description, String defaultValue,
                  List<String> options);
```

`ConfigType`：`STRING` / `TEXT` / `INT` / `BOOL` / `SELECT`。

```java
ctx.config().define("greeting", ConfigType.STRING, "问候语", "描述", "Hello");
ctx.config().define("maxCount", ConfigType.INT, "最大次数", "计数器上限", "5");
ctx.config().define("enabled", ConfigType.BOOL, "启用", "是否启用", "true");
ctx.config().defineSelect("mode", "运行模式", "仅演示", "auto", List.of("auto", "fast", "safe"));
```

### 8.2 读取配置

```java
String  getString(String key, String def);
int     getInt(String key, int def);
boolean getBoolean(String key, boolean def);
List<ConfigEntry> entries();
Map<String, String> values();
```

值存储于 `addons/<插件名>/config.yml`；`/addons` 管理页可编辑并**保存即热重载**。

### 8.3 读取宿主配置

```java
Map<String, Object> panelConfig();   // host/port/sessionTimeout/maxLoginAttempts/lockoutDuration/rateLimitPerSecond/theme/language
Map<String, Object> daemonConfig();  // host/port/name/whiteListEnabled/authTimeout/maxFileTasks/maxZipSize/outputBufferSize
Path dataDir();                      // 全局数据目录
```

## 9. 数据存储 `DataService`

```java
void put(String key, String value);
String get(String key);
void delete(String key);
Map<String, String> all();
```

持久化到 SQLite `addon_data` 表，按插件隔离。

## 10. 服务参考

所有服务按节点（`nodeId`）操作，可对接**远端 Daemon**。节点不可达或 only-daemon 下无节点服务时返回空值/失败。

### 10.1 `NodeService`

| 方法                                    | 说明                                    |
|-----------------------------------------|-----------------------------------------|
| `List<Map<String,Object>> listNodes()`  | 全部节点（id/name/url/status/style）    |
| `Map<String,Object> getNode(String id)` | 单节点，不存在返回 null                 |
| `int nodeStatus(String id)`             | 在线状态（1 在线 / 0 离线 / -1 不存在） |

### 10.2 `AppService`

| 方法                                              | 说明                         |
|---------------------------------------------------|------------------------------|
| `listApps(nodeId)`                                | 应用列表                     |
| `createApp(nodeId, name, command, args, workDir)` | 创建应用，返回含 `id` 的 Map |
| `startApp / stopApp / restartApp(nodeId, appId)`  | 启停/重启                    |
| `appLogs(nodeId, appId, count)`                   | 最近日志行                   |
| `signalApp(nodeId, appId, signal)`                | 发送信号（如 `SIGTERM`）     |

### 10.3 `FileService`

| 方法                               | 说明               |
|------------------------------------|--------------------|
| `listFiles(nodeId, path)`          | 目录列表           |
| `readFile(nodeId, path)`           | 读文件内容（文本） |
| `writeFile(nodeId, path, content)` | 写文件             |
| `deleteFile(nodeId, path)`         | 删除文件           |
| `createDirectory(nodeId, path)`    | 创建目录           |

### 10.4 `MonitorService`

| 方法               | 说明                                      |
|--------------------|-------------------------------------------|
| `snapshot(nodeId)` | 系统快照（cpuUsage/memory/network/disks） |

### 10.5 `ExecService`

| 方法                                          | 说明           |
|-----------------------------------------------|----------------|
| `ExecResult exec(nodeId, command, timeoutMs)` | 在节点执行命令 |

```java
public record ExecResult(int exitCode, String stdout, String stderr, boolean timedOut) {
    public boolean success() { return exitCode == 0; }
}
```

> 命令通过 `ProcessBuilder` 直接传参执行（无 shell 注入）。演示：
> `ctx.exec().exec(nodeId, "nginx -t", 5000)`、`ctx.exec().exec(nodeId, "nginx -s reload", 5000)`。

### 10.6 `UserService`

| 方法          | 说明                                  |
|---------------|---------------------------------------|
| `listUsers()` | 全部用户（id/username/role/roleName） |
| `getUser(id)` | 单用户                                |

### 10.7 `LogService`

| 方法                             | 说明                                            |
|----------------------------------|-------------------------------------------------|
| `record(action, target, detail)` | 写入操作审计日志（来源标记为 `addon:<插件名>`） |

## 11. 日志与调度

```java
ctx.logger().info("xx {}", val);     // 格式同 SLF4J {}
ctx.scheduler().scheduleAtFixedRate(() -> ctx.logger().debug("tick"),
        0, 30, TimeUnit.SECONDS);    // 插件专用单线程调度器
```

## 12. 权限与隔离

- 面板路由默认权限 `permission.assign`（root（或拥有 permission.assign 的用户））；`AddonContext.PUBLIC` 放行为任意登录用户
- 每插件独立类加载器（**子优先**：插件内嵌依赖优先于宿主），`im.xz.cn.lingconsole.addon.*` API 包强制使用宿主版本
- 单插件加载/运行失败不影响宿主与其他插件

## 13. only-daemon 模式

`--webui false`，插件系统同样加载：控制台指令、Daemon 路由、Socket 事件、配置/数据服务可用；依赖面板的节点/用户/日志服务返回空值；面板路由/菜单/反代无 Panel 载体时无效。

## 14. 管理

- 管理页 `/addons`（root（或拥有 permission.assign 的用户））：查看状态、编辑配置、**保存并热重载**
- 热重载自动清理并重建：面板/Daemon 路由、Socket 事件、反代、菜单、控制台指令
- 控制台 `addons` 指令列出全部插件并标注 `[OK]` / `[ERR]`
