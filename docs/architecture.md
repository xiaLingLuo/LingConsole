# 架构与安全

## 组件

```text
Browser
   | HTTP/WS
Panel :55600
   | Daemon Key over HTTP/WS
   +---- Daemon :55700 ---- files, processes, PTY, monitor
   +---- Remote Daemon
```

| Gradle 模块          | 职责                                        |
|----------------------|---------------------------------------------|
| `lingconsole-api`    | 稳定的插件接口                              |
| `lingconsole-common` | 配置、Socket 协议、权限、插件加载等共享实现 |
| `lingconsole-daemon` | 文件、应用进程、终端、归档和监控            |
| `lingconsole-app`    | 启动器、Panel、数据库、WebUI 和插件上下文   |

默认模式在同一 JVM 内启动 Panel 和本地 Daemon。`--webui false` 仅启动 Daemon，`--damon false` 仅启动 Panel。

## 认证与授权

Panel 密码使用 Argon2id。会话令牌存储于 SQLite，通过 Cookie 或 `X-LingConsole-Token` 使用；封禁、改密和登出会撤销令牌并关闭索引到该令牌或用户的 Socket。

普通用户权限来自权限组，root 固定为 `*`。权限匹配支持精确键、单段 `*` 和后缀 `.*`。应用写/高级权限只隐含相同应用的读取权限，不隐含文件或终端。

Daemon 使用 Key 作为机器控制面凭据。REST 通过 `X-LingConsole-Key`，控制 Socket 在 `auth` 事件中提交 Key。认证失败、公开接口、API、敏感操作、Socket 消息和连接均有限速或容量限制。可选 IP 白名单同时约束 REST 与 Socket。

## Web 安全

- 非 GET/HEAD Panel API 校验 `Origin`；可配置额外 Origin 和受信任代理 Host。
- Cookie 为 `HttpOnly`、`SameSite=Strict`。
- 响应设置 CSP、`X-Frame-Options: DENY`、`nosniff` 和 Referrer Policy。
- 节点 Key 通过 JSON `WRITE_ONLY` 防止回显。
- 登录正文、密码哈希并发、终端票据和下载均有限额。

## 文件与进程安全

节点文件管理的全文件能力是产品设计。应用文件使用规范化路径并默认逐级拒绝符号链接和 Windows reparse/junction；保护关闭后不应把应用目录视为隔离边界。

目录列表限制为 10000 项，文本在线读取限制为 100 KiB。归档在执行前后检查路径、类型、条目数、展开大小、深度和可用空间，并在临时目录解压后合并。下载和重型文件任务有并发与超时限制。

应用通过参数数组启动，可配置运行用户。每个进程的日志使用环形行缓冲，超长无换行输出按 64 KiB 字符切段。`exec` 接口使用 shell 执行管理员指定命令并缓存结果，这是受信任管理面的预期能力。

## 插件边界

插件 JAR 由管理员放入 `addons/`，在 LingConsole JVM 内拥有代码执行能力，不是沙箱。Panel HTTP 路由默认要求 `lingconsole.permission.assign`，Panel Socket 事件必须声明权限。Daemon 扩展只通过 `registerDaemonRoute` 暴露并由 Daemon Key 保护；Panel Socket 事件不会自动挂载到 Daemon Socket。

插件反向代理可访问插件指定的后端，属于可信插件能力。默认不转发 Cookie、Authorization、Daemon Key 或 hop-by-hop 请求头；敏感头必须显式列入白名单。

## 部署边界

系统支持受信任内网中的 HTTP/WS，但明文无法抵御同网段窃听、篡改或凭据劫持。公网、跨机房或不受信任网络必须使用 HTTPS/WSS 反向代理、防火墙和访问控制。默认监听 `0.0.0.0`，部署者应按拓扑收紧。

首次密码可能进入服务日志或 journal，需限制读取和保留范围并立即改密。应用进程不是不受信任代码沙箱；强隔离应由 Docker 等容器提供。
