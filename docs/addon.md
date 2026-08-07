# 插件 API 1.2.9

插件 API 由 `lingconsole-api.jar` 提供。插件在 LingConsole JVM 内运行，安装插件即代表你信任插件代码。

## 描述文件

JAR 根目录必须包含 `addon.toml`：

```toml
name = "myaddon"
version = "1.2.9"
main = "com.example.MyAddon"
author = "Example"
description = "Example addon"
api-version = "1.2.9"
dependencies = ["required-addon"]
soft-dependencies = ["optional-addon"]
```

`name` 是权限、命令和资源所有权的命名空间，必须唯一。硬依赖缺失或形成硬依赖环时插件不会加载；软依赖存在时用于调整加载顺序。

## 生命周期

```java
public interface Addon {
    default void onLoad(AddonContext context) {}
    default void onEnable(AddonContext context) {}
    default void onDisable() {}
}
```

`onLoad` 用于注册配置和扩展，`onEnable` 启动业务，`onDisable` 释放插件自行持有的资源。宿主会关闭插件上下文、调度器和类加载器，并注销路由、命令、权限、菜单、代理和 Socket 事件。生命周期异常只会将该插件标记为 `ERROR`。

## AddonContext

| 方法 | 说明 |
|---|---|
| `info()`, `logger()` | 描述信息与插件日志 |
| `nodes()`, `apps()`, `files()` | 节点、应用和文件服务 |
| `monitor()`, `exec()` | 监控和远程命令服务 |
| `users()`, `logs()` | 用户查询和审计记录 |
| `data()` | SQLite 插件 KV 数据 |
| `config()` | 声明式 YAML 配置 |
| `registerPanelRoute(...)` | Panel HTTP API |
| `registerDaemonRoute(...)` | Daemon Key 保护的 HTTP API |
| `registerSocketEvent(...)` | 仅 Panel 的权限化 Socket 事件 |
| `registerPanelProxy(...)` | Panel 反向代理 |
| `registerPermission(...)` | 动态权限节点 |
| `registerPanelMenu(...)` | 面板菜单 |
| `registerCommand(...)` | 控制台命令 |
| `scheduler()` | 插件专用单线程调度器 |
| `dataDir()`, `addonDataDir()` | 全局和插件私有目录 |

上下文关闭后继续调用其服务适配器会抛出 `IllegalStateException`。

## Panel 路由

```java
ctx.registerPanelRoute(AddonRouteMethod.GET, "/status",
        h -> h.json(Map.of("ok", true)), "status.read");
```

挂载到 `/api/addon/<addonName>/status`。相对权限自动变为 `<addonName>.status.read`。不传权限时默认要求 `lingconsole.permission.assign`；传 `AddonContext.PUBLIC` 时允许任意已登录用户。Panel 通用认证、Origin 校验和安全响应头仍然生效。

处理器类型为 `AddonRouteHandler`，参数是 Javalin 7 `Context`。支持 `GET`、`POST`、`PUT`、`DELETE`、`PATCH`、`HEAD` 和 `OPTIONS`。

## Daemon 路由

```java
ctx.registerDaemonRoute(AddonRouteMethod.GET, "/health",
        h -> h.json(Map.of("status", "ok")));
```

挂载到 `/consoleapi/addon/<addonName>/health`，由 Daemon Key 和 Daemon IP/速率策略保护。Daemon 没有 Panel 用户身份，因此这里不接受用户权限键。需要实时 Panel 用户授权时使用 Panel 路由或 Panel Socket。

## Panel Socket

```java
ctx.registerSocketEvent("/panel", "myaddon:refresh", "status.read",
        (connection, event, data) -> connection.emit(event, data));
```

Socket 事件只注册到 Panel Socket 服务器，调用时重新验证 Panel 会话和声明权限。`requiredPermission` 必填，可使用相对插件权限、完整 `lingconsole.*` 权限或 `AddonContext.PUBLIC`。事件不会挂载到 Daemon Socket。

`AddonSocketConnection` 提供 `sessionId()`、`emit()` 和 `close()`。

## Panel 反向代理

```java
ctx.registerPanelProxy("/service", "http", "127.0.0.1", 8080, "/api",
        "service.use", Set.of("authorization"));
```

访问 `/api/addon/<addonName>/service/...` 时转发到插件声明的后端。默认权限为 `lingconsole.permission.assign`。默认只转发安全请求头，不转发 Cookie、Authorization、`X-LingConsole-Token`、Daemon Key 或 hop-by-hop 头；额外头必须以小写名称显式列出。代理目标由可信插件控制，具备 SSRF 能力。

## 配置与数据

```java
ctx.config().define("enabled", ConfigType.BOOL, "Enabled", "", "true");
ctx.config().defineSelect("mode", "Mode", "", "safe", List.of("safe", "fast"));
boolean enabled = ctx.config().getBoolean("enabled", true);

ctx.data().put("cursor", "42");
String cursor = ctx.data().get("cursor");
```

配置类型为 `STRING`、`TEXT`、`INT`、`BOOL`、`SELECT`，存储于 `addons/<name>/config.yml`。KV 数据存储在 Panel SQLite 的 `addon_data` 表并按插件隔离。`ConfigService.panelConfig()` 和 `daemonConfig()` 返回允许公开给插件的宿主配置摘要，不包含 Daemon Key。

## 服务接口

| 服务             | 主要方法                                                                             |
|------------------|--------------------------------------------------------------------------------------|
| `NodeService`    | `listNodes`, `getNode`, `nodeStatus`                                                 |
| `AppService`     | `listApps`, `createApp`, `startApp`, `stopApp`, `restartApp`, `appLogs`, `signalApp` |
| `FileService`    | `listFiles`, `readFile`, `writeFile`, `deleteFile`, `createDirectory`                |
| `MonitorService` | `snapshot`                                                                           |
| `ExecService`    | `exec(nodeId, command, timeoutMs)`                                                   |
| `UserService`    | `listUsers`, `getUser`                                                               |
| `LogService`     | `record(action, target, detail)`                                                     |

这些服务是可信插件的宿主能力，不继承当前浏览器用户的权限。插件 Panel 入口必须先声明合适的 requiredPermission。Panel 不存在的 only-daemon 模式中，依赖数据库或节点连接的服务可能返回空结果或失败。

## 类加载与管理

每个插件使用独立的子优先类加载器，但 `im.xz.cn.lingconsole.addon.*` 强制使用宿主 API。拥有 `lingconsole.permission.assign` 的用户可以在插件页查看状态、修改配置和热重载。控制台 `addons` 显示加载状态。

完整示例见 [插件开发教程](plugin-guide.md) 和仓库 `exampleAddon/`。
