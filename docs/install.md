# 安装、配置与构建

## 环境要求

- 运行和构建均使用 JDK 25。
- Linux 一键安装必须以 root 执行，并需要 `wget` 或 `curl`、`tar`。
- Windows 建议使用管理员终端运行涉及系统管理的功能。

## Linux 一键安装

```bash
sudo su -c "wget -qO- https://lingconsole.xzrui.cn/install.sh | bash"
```

安装器会将程序部署到 `/opt/lingConsole`，数据放在 `/lingConsole`，按需从 Adoptium 获取 JDK 25，注册 `lingconsole` 命令，并优先创建 systemd 服务。

```bash
lingconsole start
lingconsole end
systemctl status lingconsole
```

发布包应为安装资源填写 SHA-256。开发树中的空校验值表示跳过校验，不应作为正式发布配置长期使用。

## 手动运行

```bash
java -jar LingConsole.jar
```

首次启动会创建配置、SQLite 数据库、Daemon Key 和 root 初始密码。默认访问 `http://<host>:55600`。初始密码文件默认位于 `/lingConsole/first-launch-password.txt`；完成首次登录和改密后应确认该文件已删除。

## 启动参数

| 参数                            |         默认值 | 说明                                  |
|---------------------------------|---------------:|---------------------------------------|
| `--webui <true/false>`          |         `true` | 是否启动 Panel                        |
| `--damon <true/false>`          |         `true` | 是否启动 Daemon；参数名保留为 `damon` |
| `--config <path>`               | `/lingConsole` | 数据目录                              |
| `--data-dir <path>`             | `/lingConsole` | `--config` 的别名                     |
| `--singleUserMode <true/false>` |        `false` | 仅允许 root 账户，禁用多用户功能      |
| `--version`, `-v`               |              - | 输出版本号                            |
| `--help`, `-h`                  |              - | 输出帮助                              |

```bash
java -jar LingConsole.jar --webui false
java -jar LingConsole.jar --damon false
java -jar LingConsole.jar --data-dir /srv/lingconsole
```

## 数据目录

```text
/lingConsole/
|-- web/
|   |-- config.toml
|   |-- data/lingconsole.db
|   `-- static/ and templates/
|-- damon/config.toml
|-- apps/<appId>/
|-- addons/*.jar
|-- logs/
`-- first-launch-password.txt
```

目录、数据库、Daemon Key、日志和插件均属于敏感资产。Linux 安装器使用严格 `umask` 并收紧权限；自定义部署需自行保持等价权限。

## Panel 配置

文件：`web/config.toml`。

| 配置                                         |                    默认值 | 说明                                |
|----------------------------------------------|--------------------------:|-------------------------------------|
| `server.host`, `server.port`                 |        `0.0.0.0`, `55600` | 监听地址                            |
| `auth.sessionTimeout`                        |                    `3600` | 会话秒数                            |
| `auth.maxLoginAttempts`                      |                       `5` | 登录失败阈值                        |
| `auth.lockoutDuration`                       |                     `900` | 锁定秒数                            |
| `security.rateLimitPerSecond`                |                       `8` | 每 IP 登录速率                      |
| `security.loginBodyMaxBytes`                 |                    `8192` | 登录正文上限                        |
| `security.loginMaxConcurrent`                |                       `8` | 并发登录处理上限                    |
| `security.passwordVerificationConcurrency`   |                       `2` | 密码哈希并发数                      |
| `security.passwordVerificationTimeoutMillis` |                     `250` | 等待密码校验许可时间                |
| `security.firstLaunchPasswordFile`           |            数据目录下文件 | 初始密码文件                        |
| `security.terminalTicket*`                   |           `60/1000/10/10` | TTL、全局、每用户、每 IP 每分钟限额 |
| `security.externalOrigins`                   |                      `[]` | 额外允许的完整 Origin               |
| `security.trustedHosts`                      |                      `[]` | 反向代理 Host 白名单                |
| `download.maxConcurrent*`                    |                   `8/4/2` | 全局、每节点、每用户下载并发        |
| `socket.*`                                   |                见默认配置 | 消息、连接、认证超时和事件速率限制  |
| `database.path`                              | `web/data/lingconsole.db` | SQLite 路径                         |

## Daemon 配置

文件：`damon/config.toml`。

| 配置                            |                 默认值 | 说明                             |
|---------------------------------|-----------------------:|----------------------------------|
| `server.host`, `server.port`    |     `0.0.0.0`, `55700` | 监听地址                         |
| `auth.key`                      |           首次启动生成 | REST 和控制 Socket 凭据          |
| `auth.name`                     |         `local-daemon` | 节点名称                         |
| `auth.whiteListEnabled`         |                `false` | 是否启用 IP 白名单               |
| `auth.whiteListIps`             |               loopback | 白名单地址                       |
| `auth.authTimeout`              |                    `6` | Socket 认证秒数                  |
| `auth.successUnlockOnceEnabled` |                `false` | 锁定期间是否允许一次正确凭据解锁 |
| `instance.defaultAppPath`       |    `/lingConsole/apps` | 应用根目录                       |
| `instance.maxFileTasks`         |                    `2` | 重型文件任务并发                 |
| `instance.outputBufferSize`     |                  `256` | 每应用保留日志行数               |
| `instance.softShutdown*`        |              `true/30` | 优雅停止及等待秒数               |
| `archive.compress.*`            | `100000/209715200/120` | 条目、总字节和超时               |
| `archive.extract.*`             | `100000/209715200/120` | 解压资源限制                     |
| `download.*`                    |           `16/30/3600` | 并发、空闲读取和最长持续时间     |
| `socket.*`                      |             见默认配置 | 消息、连接和事件速率限制         |
| `terminal.shellMode`            |                 `auto` | Shell 选择模式                   |

## 构建产物

```bash
gradlew.bat clean build
```

根项目构建会运行测试并生成：

- `build/libs/LingConsole.jar`：可执行 fat JAR。
- `build/libs/lingconsole-api.jar`：插件编译 API。

示例插件依赖上一步生成的 API JAR：

```bash
gradlew.bat -p exampleAddon clean build
```

产物：`exampleAddon/build/libs/exampleAddon.jar`。也可以执行 `exampleAddon/build.bat` 或 `build.sh` 进行独立构建，此时产物位于 `exampleAddon/exampleAddon.jar`。
