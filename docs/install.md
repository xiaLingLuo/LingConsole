# 安装部署

> 环境要求: **Java 25**

## 一键安装 (Linux)

```bash
sudo su -c "wget -qO- https://raw.githubusercontent.com/xiaLingLuo/LingConsole/refs/heads/main/installer/install.sh | bash"
```

`install.sh` 会：
1. 检查 root 权限
2. 全新安装时部署Java25
3. 部署到 `/opt/lingConsole`
4. 预建数据目录 `/lingConsole`
5. 注册系统命令 `lingconsole`
6. 注册开机自启并立即启动

> **同一命令也是更新入口**：若已安装，则执行命令会进行更新操作。

```bash
lingconsole start
lingconsole end
systemctl status lingconsole
```

卸载：

```bash
systemctl disable lingconsole; rm -f /usr/local/bin/lingconsole; rm -rf /opt/lingConsole /lingConsole
```

## 手动安装

```bash
# 1. 安装 Java 25 (Debian/Ubuntu)
sudo apt install openjdk-25-jdk-headless
#    (RedHat/Fedora)
sudo dnf install java-25-openjdk-headless

# 2. 下载 LingConsole.jar 并运行
java -jar LingConsole.jar
```

## 示例启动脚本

在本仓库/start目录中，存在windows和linux的示例启动脚本，你可直接使用。

## 启动参数

| 参数                             | 默认           | 说明                                           |
|----------------------------------|----------------|------------------------------------------------|
| `--webui <true\|false>`          | `true`         | 是否启动 Panel (WebUI)                         |
| `--damon <true\|false>`          | `true`         | 是否启动 Daemon                                |
| `--config <path>`                | `/lingConsole` | 数据目录                                       |
| `--singleUserMode <true\|false>` | `false`        | 单用户模式：仅 root(ling) 可用，完全无用户系统 |
| `--version` / `-v`               | -              | 输出版本号（更新脚本据此判断是否有新版本）     |
| `--help`                         | -              | 查看帮助                                       |

```bash
java -jar LingConsole.jar --webui false          # 仅启动 Daemon (55700)
java -jar LingConsole.jar --damon false          # 仅启动 Panel (55600)
java -jar LingConsole.jar --config /data/lc      # 自定义数据目录
java -jar LingConsole.jar --singleUserMode true  # 开启单用户模式, 仅 root 登录
```

## 附带启动脚本 `start/`

`start/` 目录提供 `start.sh` / `start.bat` 一键启动，配合 `config.txt` 调整：

```ini
jarName=LingConsole.jar
java_path=default
MaxRAM=auto
MinRAM=auto
web=true
damon=true
singleUserMode=true
```

## 数据目录 `/lingConsole`

```
/lingConsole/
├── web/                  # Panel 配置 + SQLite 数据库 + WebUI 静态资源
├── damon/                # Daemon 配置 (含自动生成的 Key) + 数据
├── apps/                 # 应用实例 (按 UUID 隔离)
├── logs/                 # 运行日志
└── addons/               # 插件目录 (放 *.jar)
```

## 端口

| 服务   | 端口  | 说明                         |
|--------|-------|------------------------------|
| Panel  | 55600 | 页面 / `/api` / `/socket.io` |
| Daemon | 55700 | `/consoleapi` / `/socket.io` |

## 构建

```bash
./gradlew build          # Windows: gradlew.bat build
./gradlew test           # 运行全部测试
# 产物: build/libs/LingConsole.jar (单一 fat JAR)
```
