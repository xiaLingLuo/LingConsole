# 权限节点

普通用户权限全部来自权限组，root 账户 `ling` 固定拥有 `*`。

## 匹配规则

| 形式     | 示例                          | 含义                 |
|----------|-------------------------------|----------------------|
| 精确     | `lingconsole.log.read`        | 只匹配该键           |
| 对象     | `lingconsole.node.read.node1` | 指定对象             |
| 后缀通配 | `lingconsole.node.read.*`     | 匹配该前缀下所有子键 |
| 分段通配 | `lingconsole.*.read`          | `*` 匹配一个点分段   |
| 全局     | `*`                           | 匹配全部权限         |

`<appId>` 在整个 Panel 中唯一；服务端仍会核验应用属于 URL 指定节点。

## 全局权限

| 权限                            | 能力                                            |
|---------------------------------|-------------------------------------------------|
| `lingconsole.user.manage`       | 管理普通用户，不能修改 root                     |
| `lingconsole.permission.assign` | 管理权限组及用户分配；可创建 `*` 组，等同管理员 |
| `lingconsole.user.banned`       | 被分配者禁止登录并撤销会话                      |
| `lingconsole.log.read`          | 读取全局操作日志                                |
| `lingconsole.system.status`     | 读取系统状态                                    |
| `lingconsole.packages.manage`   | 跨节点 APT 及 7-Zip 管理                        |
| `lingconsole.dashboard.admin`   | 完整管理仪表盘                                  |
| `lingconsole.dashboard.user`    | 个人仪表盘和本人日志                            |

## 节点权限

| 权限模式                             | 能力                     |
|--------------------------------------|--------------------------|
| `lingconsole.node.read.<nodeId>`     | 查看节点和连接状态       |
| `lingconsole.node.write.<nodeId>`    | 修改、删除和调整节点样式 |
| `lingconsole.file.node.<nodeId>`     | 完整节点文件系统管理     |
| `lingconsole.terminal.node.<nodeId>` | 节点系统终端             |
| `lingconsole.monitor.read.<nodeId>`  | 节点监控                 |

每一项均可将 `<nodeId>` 替换为 `*`。只有 `lingconsole.node.write.*` 允许创建新节点。修改节点 URL 或 Key 还要求 `lingconsole.permission.assign`。

## 应用权限

| 权限模式                           | 能力                                                       |
|------------------------------------|------------------------------------------------------------|
| `lingconsole.app.read.<appId>`     | 详情、状态和日志                                           |
| `lingconsole.app.write.<appId>`    | 普通配置、启停、重启和删除，并隐含读取                     |
| `lingconsole.app.advanced.<appId>` | 命令、参数、环境、工作目录、运行用户和文件保护，并隐含读取 |
| `lingconsole.file.app.<appId>`     | 应用工作目录文件管理                                       |
| `lingconsole.terminal.app.<appId>` | 应用交互终端                                               |

每一项均支持 `.*`。创建应用要求 `lingconsole.app.write.*`。写或高级权限不隐含文件或终端。

## 基础分类键

`lingconsole.node.read`、`lingconsole.node.write`、`lingconsole.app.read`、`lingconsole.app.write`、`lingconsole.app.advanced`、`lingconsole.file.node`、`lingconsole.file.app`、`lingconsole.terminal.node`、`lingconsole.terminal.app`、`lingconsole.monitor.read` 用于权限目录和界面分类，本身不授权具体对象。

## 插件权限

插件相对键 `manage` 会标准化为 `<addonName>.manage`。Panel 路由未声明权限时默认要求 `lingconsole.permission.assign`；Panel Socket 事件必须显式声明权限；`AddonContext.PUBLIC` 表示任意已登录 Panel 用户。Daemon 路由不使用用户权限，统一由 Daemon Key 保护。

## 授权建议

优先授予具体节点或应用，谨慎授予 `.*`。`permission.assign`、`packages.manage`、节点文件、节点终端和应用高级配置均应视为高风险权限。应用运维通常组合 `app.write`、`file.app` 和按需的 `terminal.app`，不应默认包含 `app.advanced`。
