# 泠 控制面板
## LingConsole

> 服务器底层 WebUI 管理程序 · 跨平台 · 单一 JAR · Java 25

LingConsole 将多台服务器的守护进程（Daemon）统一接入一个 Web 控制面板（Panel），提供节点、应用、文件、终端、系统监控与包管理能力，并内置插件系统与细粒度权限组，以AGPL-3.0协议进行开源。

仓库地址：https://github.com/xiaLingLuo/LingConsole

---

## ✨ 核心功能

| 功能          | 说明                                                                                    |
|---------------|-----------------------------------------------------------------------------------------|
| ⬢ 多节点管理  | 一个 Panel 对接多个远端 Daemon（`ws://` / `wss://` + Daemon Key），节点 ID 由用户自定义 |
| ▤ 应用管理    | 创建/启动/停止/重启应用，卡片式管理，自动重启策略，应用 ID 自定义                       |
| 🖥 WebShell   | xterm.js 全屏终端（节点终端 / 应用终端），PTY 支持                                      |
| 📁 文件管理   | 全目录浏览、上传/下载/在线编辑、多选批量操作、复制、压缩/解压（7-Zip）                  |
| ⬓ 包管理器    | Debian 系图形化 apt：更新源/升级/智能升级/清理、搜索与安装                              |
| ◔ 系统监控    | CPU / 内存 / 磁盘 / 网络实时图表                                                        |
| ♛ 权限组系统 | 精细权限节点系统，按节点/应用细分权限键                                                 |
| 🔌 插件系统   | 简单易安装，扩展方便                                                                    |
| ≣ 操作日志    | 全操作审计记录                                                                          |
| ⚙ 系统设置   | 配置展示、版本与仓库信息                                                                |

---

## 🚀 一键安装 (Linux)

```bash
sudo su -c "wget -qO- https://lingconsole.xzrui.cn/install.sh | bash"
```

脚本自动探测 `apt` / `dnf` / `yum`、安装 Java 25、部署到 `/opt/lingConsole`，并注册系统命令 `lingconsole start|end`。

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
