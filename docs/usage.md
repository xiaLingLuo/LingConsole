 # WebUI 使用指南

> 浏览器访问 `http://<服务器IP>:55600`，使用 root 账号（`ling`，初始密码见控制台横幅）登录。

## 功能总览

| 功能            | 说明                                                                   |
|-----------------|------------------------------------------------------------------------|
| 📊 仪表盘       | 节点状态、CPU/内存概览、系统信息、内存趋势图、JVM 运行时间             |
| ⬢ 节点管理      | 卡片式管理多个远端 Daemon 节点（`ws://` / `wss://` + Daemon Key）      |
| ▤ 应用管理      | 创建/启动/停止/重启应用，卡片式管理，自动重启策略                      |
| 🖥 WebShell     | xterm.js 全屏终端（节点终端 / 应用终端）                               |
| 📁 文件管理     | 全目录浏览、上传/下载/在线编辑、多选批量操作、复制、压缩/解压（7-Zip） |
| ⬓ 包管理器      | Debian 系 apt 图形化管理（更新源/升级/智能升级/清理/卸载/搜索安装）    |
| ◔ 系统监控      | CPU / 内存 / 磁盘 / 网络实时图表                                       |
| 👥 用户与权限组 | 用户 + 权限组系统                                                      |
| ≣ 操作日志      | 全操作审计记录                                                         |
| ⚙ 系统设置     | 配置展示、版本/仓库信息                                                |

## 节点管理

- 添加节点：填写**节点 ID**（仅小写字母/数字，创建后不可改）、节点名称、Daemon 地址（`ws://IP:55700` 或 `wss://...`）与 **Daemon Key**
- Daemon Key 由 Daemon 首次启动自动生成，存放于 `/lingConsole/damon/config.toml`
- 节点 Key 仅存于服务器配置，**WebUI 不回显**
- 一个 Panel 可管理多台仅运行 Daemon 的服务器

## 应用管理

- 创建应用：填写**应用 ID**（仅小写字母/数字）、名称、启动命令等
- 卡片显示 ID、命令、PID、运行时间与最近日志；支持启动/停止/重启/高级配置/终端/文件
- 应用访问范围由权限节点控制（见下）
- 应用可指定启动用户，从而限制权限范围

## 文件管理

- 全目录面包屑导航、上传/下载、在线编辑
- 多选 + 批量操作：全选、批量压缩、复制选中、批量下载、批量删除
- 压缩/解压：基于 7-Zip，需要在包管理器中安装7zip才可使用

## 用户与权限组

- 用户权限 **100% 来自所分配的权限组**
- 权限键按**节点/应用细分**：
  - 节点级：`lingconsole.node.read.<节点ID>`、`lingconsole.node.write.<节点ID>`、`lingconsole.file.node.<节点ID>`、`lingconsole.terminal.node.<节点ID>`、`lingconsole.monitor.read.<节点ID>`
  - 应用级：`lingconsole.app.read|write|advanced.<节点ID>.<应用ID>`、`lingconsole.file.app.<节点ID>.<应用ID>`、`lingconsole.terminal.app.<节点ID>.<应用ID>`
  - 基础键（如 `lingconsole.node.read`）授予全部节点/应用；`*` / `.*` 通配可用
- **应用访问**由应用权限节点控制
- **封禁用户**：创建包含 `lingconsole.user.banned` 权限的"封禁"权限组并分配即可；默认无人被封
- `permission.assign` 可创建/分配权限组；`user.manage` 管理用户
- root 登录账号 `ling` 与 `root` 等效
- 单用户模式（默认**关闭**）：开启后仅 root 可用，完全隐藏用户系统

## 包管理器

- 仅支持 **Debian 系**（Debian / Ubuntu 等）
- 列出已安装包、更新软件源（`apt update`）、升级（`apt upgrade`）、智能升级（`apt full-upgrade`）、清理无用依赖（`apt autoremove`）、卸载
- **安装新软件**：输入关键字 → `apt search` 智能搜索 → 选择 → `apt-get install`

## 插件管理 `/addons`

root 可用：查看已安装插件状态、编辑配置、**保存并热重载**。详见 [插件 API 文档](addon)。
