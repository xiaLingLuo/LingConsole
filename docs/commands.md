# 控制台命令

运行 JAR 的标准输入支持以下格式：

```text
[namespace:]command [arguments...]
```

省略命名空间时使用 `lingconsole`。因此 `addons` 等价于 `lingconsole:addons`。

## 内置命令

| 命令 | 说明 |
|---|---|
| `addons` | 列出插件及 `[OK]`/`[ERR]` 状态 |
| `end` | 触发 JVM 关闭流程 |
| `stop` | `end` 的别名 |

关闭流程会停用插件、停止 Panel/Daemon、关闭数据库和调度器。服务方式运行时也可使用 systemd 或启动脚本停止程序。

## 插件命令

插件使用 `registerCommand` 注册在自身名称空间下：

```java
ctx.registerCommand("status", (command, args, sender) ->
        sender.sendMessage("running"));
```

执行 `myaddon:status`。插件热重载或停用时，其全部命令会注销。不同插件可以使用同名命令，但插件名称必须唯一。
