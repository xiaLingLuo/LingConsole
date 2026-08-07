# LingConsole 1.2.9

![java](https://img.shields.io/badge/Java-25-ED8B00)
![license](https://img.shields.io/badge/License-AGPL--3.0-blue)

LingConsole 是基于 Java 25 的服务器 Web 管理系统。一个 Panel 可以通过 Daemon Key 管理本机或多台远端 Daemon，提供节点、应用进程、文件、Web 终端、监控、Debian 软件包、用户权限、审计日志和插件扩展能力，通过AGPL-3.0协议开源。

## 功能

| 模块 | 能力                                                                  |
|------|-----------------------------------------------------------------------|
| 节点 | 连接多个 `ws://` 或 `wss://` Daemon，查看状态并按节点授权             |
| 应用 | 创建、配置、启动、停止、重启、自动重启、日志与交互终端                |
| 文件 | 节点全文件系统或应用工作目录的读写、上传、下载、复制和归档            |
| 终端 | 节点系统 PTY 和运行中应用的交互终端                                   |
| 运维 | CPU、内存、磁盘、网络监控及 Debian/Ubuntu APT 管理                    |
| 安全 | Argon2id、会话撤销、权限组、对象级授权、操作日志和资源限额            |
| 插件 | 生命周期、配置、数据、Panel/Daemon 路由、Panel Socket、菜单和服务 API |

## 快速开始

运行环境为 Java 25。Linux 一键安装：

```bash
sudo su -c "wget -qO- https://lingconsole.xzrui.cn/install.sh | bash"
```

手动启动：

```bash
java -jar LingConsole.jar
```

默认数据目录为 `/lingConsole`，Panel 监听 `55600`，Daemon 监听 `55700`。首次启动会生成 root 账户 `ling` 的随机密码，并写入受保护的 `first-launch-password.txt`。首次登录后应立即修改密码。

## 部署边界

LingConsole 预期拥有服务器管理权限，WebShell、全文件管理和命令执行属于核心能力。HTTP/WS 明文仅适用于受信任内网；公网或不受信任网络应当使用 HTTPS/WSS 反向代理、防火墙和访问控制。Daemon Key、数据库、配置、日志及首次密码文件都应限制文件读取权限。

应用工作目录限制不是恶意进程的强隔离边界。需要运行不受信任代码时，应在 Docker 等容器沙箱中运行应用。

## 构建

```bash
# Windows
gradlew.bat clean build

# Linux
./gradlew clean build
```

主体与 API 产物位于 `build/libs/LingConsole.jar` 和 `build/libs/lingconsole-api.jar`。示例插件在主体构建后执行 `gradlew.bat -p exampleAddon clean build`，产物为 `exampleAddon/build/libs/exampleAddon.jar`；独立的 `exampleAddon/build.bat` 或 `build.sh` 则在示例目录直接生成 `exampleAddon.jar`。

## 文档

- [文档首页](docs/README.md)
- [安装与构建](docs/install.md)
- [WebUI 使用](docs/usage.md)
- [架构与安全](docs/architecture.md)
- [权限节点](docs/perms.md)
- [控制台命令](docs/commands.md)
- [插件 API](docs/addon.md)
- [插件开发教程](docs/plugin-guide.md)

---

## 统计数据
![lingconsole](https://treasure.xzrui.cn/img/lingconsole "lingconsole")
