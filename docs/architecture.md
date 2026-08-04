# 架构与安全

## 架构

```
┌───────────────────────────┐          ┌──────────────────────────┐
│   Panel (端口 55600)      │          │   Daemon (端口 55700)    │
│   ├─ / 页面 (Thymeleaf)   │  HTTP+WS │   ├─ /consoleapi REST    │
│   ├─ /api REST            │◀────────▶│   ├─ /socket.io /daemon  │
│   ├─ /socket.io /panel    │  Key认证 │   └─ /socket.io /stream  │
│   └─ /static 静态资源     │          │    (文件/应用/终端/监控) │
└───────────────────────────┘          └──────────────────────────┘
        │                                     │
  浏览器直接访问                       可对接多个远端 Daemon 节点
```

- **Panel**：Web 界面 + 业务 API，管理节点、用户、权限、日志、插件
- **Daemon**：底层守护进程，执行文件操作、应用进程管理、PTY 终端、系统监控
- 节点通过完整 URL（`ws://` / `wss://`）与 Daemon Key 认证对接
- 三种运行模式：Panel+Daemon（默认）/ only-Daemon（`--webui false`）/ only-Panel（`--damon false`）

## 模块结构

| 模块                 | 说明            |
|----------------------|-----------------|
| `lingconsole-api`    | 插件 API        |
| `lingconsole-common` | 共享库          |
| `lingconsole-daemon` | Daemon 守护进程 |
| `lingconsole-app`    | 主应用          |

## 技术栈

Java 25 · Javalin 7.2.2 · Thymeleaf · SQLite (HikariCP) · Socket.IO · Argon2id · TOML · OSHI · pty4j · xterm.js · ECharts · CodeMirror

## 安全模型

- **密码哈希**：Argon2id，无明文存储
- **会话**：Cookie 带 `HttpOnly` + `SameSite=Strict`（HTTPS 下加 `Secure`）
- **节点 Key**：仅存于服务器配置文件，WebUI 不回显（任何角色都只能修改、不能查看）
- **权限体系**：权限组系统——普通用户权限 100% 来自所分配的权限组（无角色体系）；权限键按**节点/应用细分**（`lingconsole.node.read.<节点>`、`lingconsole.app.write.<节点>.<应用>` 等）；`permission.assign` 控制权限分配；`lingconsole.user.banned` 权限节点用于封禁
- **应用访问**：由应用权限节点（`lingconsole.app.*.<节点>.<应用>`）控制，无独立应用范围设置
- **Daemon 认证**：仅接受 `X-LingConsole-Key` 请求头
- **命令执行**：`ProcessBuilder` 直接传参，无 shell 注入
- **路径穿越**：应用沙箱 `PathUtil.sanitize` 防护
- **插件路由**：默认受 `permission.assign` 保护，`PUBLIC="*"` 显式放行

> ⚠ LingConsole 是服务器底层管理程序，默认以 root 运行并具备完整能力。**请勿将面板端口暴露到公网**，建议置于内网或配合防火墙/TLS。

## Treasure 上报

Treasure 会定期向 `https://treasure.xzrui.cn` 上报**匿名统计信息**。其作为插件安装，可在插件配置文件中关闭。
