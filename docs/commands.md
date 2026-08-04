# 控制台指令

> 可在启动 jar 的窗口中直接输入指令

## 格式

```
[命名空间:]指令 [参数...]
```

- **命名空间前缀可选**：直接输入 `addons` 等价于 `lingconsole:addons`
- 原生指令前缀：`lingconsole:`
- 插件指令前缀：`<插件名>:`

## 内置指令

### `addons` — 列出已安装插件

```
[09:58:09 INFO]: Console Addons (3):
[09:58:09 INFO]:  - ExampleAddon[OK], JustIt[OK], TestError[ERR]
```

- `[OK]`：插件加载/启用正常
- `[ERR]`：插件加载/启用失败，或**命名空间冲突**（同名插件双方均标记 `[ERR]`）

### `end` / `stop` — 关闭程序

```
end          # 等价 lingconsole:end
stop         # 等价 lingconsole:stop
```

输出 `正在关闭 LingConsole ...` 后触发完整关闭流程（停用插件、停止 Panel/Daemon、关闭数据库）。

## 插件注册指令

插件通过 `AddonContext.registerCommand(name, handler)` 注册，自动挂到 `<插件名>:` 命名空间：

```java
ctx.registerCommand("hello", (command, args, sender) ->
        sender.sendMessage("你好, " + ctx.info().name()));
```

执行：

```
exampleaddon:hello
```

- `CommandHandler.execute(String command, String[] args, CommandSender sender)`
- `CommandSender.sendMessage(...)` 按 `[HH:mm:ss INFO]: ` 格式输出
- 指令名可重合（不同命名空间），但**命名空间（插件名）不可重合**

## only-daemon 模式

`--webui false`（仅 Daemon）时指令系统同样可用：可查看/控制插件，插件可注册指令与 Daemon 路由。详见 [插件 API 文档](docs/addon.md)。
