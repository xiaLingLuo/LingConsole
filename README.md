# 泠 控制面板
## LingConsole

> 服务器底层 WebUI 管理程序 · 跨平台 · 单一 JAR · Java 25

LingConsole 将多台服务器的守护进程（Daemon）统一接入一个 Web 控制面板（Panel），提供节点、应用、文件、终端、系统监控与包管理能力，并内置插件系统与细粒度权限组。

---

## ✨ 核心功能

| 功能          | 说明                                                                   |
|---------------|------------------------------------------------------------------------|
| ⬢ 多节点管理  | 一个 Panel 对接多个远端 Daemon（`ws://` / `wss://` + Daemon Key）      |
| ▤ 应用管理    | 创建/启动/停止/重启应用，卡片式管理，自动重启策略                      |
| 🖥 WebShell   | xterm.js 全屏终端（节点终端 / 应用终端），PTY 支持                     |
| 📁 文件管理   | 全目录浏览、上传/下载/在线编辑、多选批量操作、复制、压缩/解压（7-Zip） |
| ⬓ 包管理器    | Debian 系图形化 apt：更新源/升级/智能升级/清理、搜索与安装             |
| ◔ 系统监控    | CPU / 内存 / 磁盘 / 网络实时图表                                       |
| ♛ 权限组系统 | 精细权限节点系统，按节点/应用细分权限键                                |
| 🔌 插件系统   | 简单易安装，扩展方便                                                   |
| ≣ 操作日志    | 全操作审计记录                                                         |
| ⚙ 系统设置   | 配置展示、版本与仓库信息                                               |

---

## 🚀 一键安装 (Linux)

```bash
sudo su -c "wget -qO- https://raw.githubusercontent.com/xiaLingLuo/LingConsole/refs/heads/master/installer/install.sh | bash"
```

脚本自动装 Java 25，部署，并注册系统命令 `lingconsole start|end`。

## 🛠 手动安装

1. 安装 **Java 25**
2. 下载 `LingConsole.jar`，放入任意空文件夹
3. 以 root（Linux）/ 管理员（windows）运行：

```bash
java -jar LingConsole.jar
```

首次启动会在 `/lingConsole/` 初始化数据目录，并在控制台输出初始账号与密码。浏览器访问 `http://<服务器IP>:55600` 登录。

> 默认关闭单用户模式，启用完整用户/权限组系统；可用 `--singleUserMode true` 切换为仅 root 可用。

## 🖥 控制台指令

在启动窗口直接输入指令：

```
addons      # 列出插件
end  # 优雅关闭程序
```

## 📚 文档导航

| 文档                            | 说明                                  |
|---------------------------------|---------------------------------------|
| [📘 API 文档](addon)            | 插件开发 API 完整参考                 |
| [📗 插件开发教程](plugin-guide) | 从零写插件的完整步骤                  |
| [安装部署](install)             | 一键/手动安装、启动参数、数据目录     |
| [WebUI 使用指南](usage)         | 面板各功能使用说明                    |
| [架构与安全](architecture)      | 架构设计、安全模型、端口              |
| [控制台指令](commands)          | 指令系统（`lingconsole:` / 插件指令） |

---

> ⚠ LingConsole 是服务器底层管理程序，需要以 root 运行并具备完整能力。**请勿将面板端口暴露到公网**，建议置于内网或配合防火墙/TLS。
