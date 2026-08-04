# 插件开发教程

> 从零开发一个 LingConsole 插件。完整可运行示例见 `exampleAddon/`。

## 0. 环境准备

- **JDK 25**（编译与运行时）
- **lingconsole-api.jar**：编译后与 fat jar 一同输出于 `build/libs/lingconsole-api.jar`；示例项目也内置了 `exampleAddon/libs/lingconsole-api.jar`
- 路由处理器使用 Javalin 7 的 `Context`，编译时需 **javalin.jar**（`exampleAddon/libs/javalin.jar`）

## 1. 项目结构

```
myaddon/
├── addon.toml
└── src/com/example/MyAddon.java
```

## 2. 描述文件 `addon.toml`

```toml
name = "myaddon"                    # 命名空间 (唯一)
version = "1.0.0"
main = "com.example.MyAddon"
author = "You"
description = "我的第一个插件"
api-version = "1.1"
# dependencies = ["other-addon"]        # 硬依赖
# soft-dependencies = ["optional-helper"]
```

## 3. 主类

```java
package com.example;

import im.xz.cn.lingconsole.addon.*;
import im.xz.cn.lingconsole.addon.service.*;
import java.util.*;

public class MyAddon implements Addon {

    @Override
    public void onLoad(AddonContext ctx) {
        // 1) 声明配置
        ctx.config().define("greeting", ConfigType.STRING, "问候语", "描述", "Hello");

        // 2) 面板路由 (PUBLIC 表示任意登录用户可访问)
        ctx.registerPanelRoute(AddonRouteMethod.GET, "/hello",
                h -> h.json(Map.of("greeting",
                        ctx.config().getString("greeting", "Hello"))),
                AddonContext.PUBLIC);

        // 3) Daemon 路由
        ctx.registerDaemonRoute(AddonRouteMethod.GET, "/ping",
                h -> h.json(Map.of("pong", true)));

        // 4) 控制台指令: myaddon:status
        ctx.registerCommand("status", (cmd, args, sender) ->
                sender.sendMessage("myaddon 运行中, 版本 " + ctx.info().version()));

        // 5) Socket 事件
        ctx.registerSocketEvent("/panel", "myaddon:hello", (conn, event, data) ->
                conn.emit("myaddon:hello", Map.of("echo", data)));

        // 6) 侧栏菜单
        ctx.registerPanelMenu("我的插件", "/api/addon/myaddon/hello");
    }

    @Override
    public void onEnable(AddonContext ctx) {
        ctx.logger().info("myaddon enabled");
    }

    @Override
    public void onDisable() {
        // 清理资源
    }
}
```

## 4. 构建

### Gradle

```kotlin
plugins { java }
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

val apiJar = file("libs/lingconsole-api.jar")
val javalinJar = file("libs/javalin.jar")
dependencies { compileOnly(files(apiJar, javalinJar)) }

tasks.jar {
    from("addon.toml")
}
```

```bash
gradle build
```

> 要求描述文件 `addon.toml` 位于 JAR 根目录。

## 5. 部署与启用

```bash
# 复制到插件目录
sudo cp myaddon.jar /lingConsole/addons/
# 重启生效
```

## 6. 验证

- 面板路由：`GET /api/addon/myaddon/hello`（需登录）
- Daemon 路由：`GET /consoleapi/addon/myaddon/ping`（需 `X-LingConsole-Key`）
- 控制台指令：在启动窗口输入 `myaddon:status`，输出 `myaddon 运行中, 版本 1.0.0`
- 插件管理页 `/addons` 中显示 `myaddon[OK]`

## 7. 热重载

在 `/addons` 管理页编辑配置并点击**保存并热重载**，或改代码后重新 `gradle build` + 复制 + 热重载。热重载会：

1. 调用旧实例 `onDisable`
2. 关闭旧类加载器
3. 用新 jar 重新加载（新类加载器、新实例）
4. 重建路由 / 指令 / 事件 / 反代 / 菜单

## 8. 服务与远程节点

所有服务接口都接受 `nodeId`，可操作**远端 Daemon**（A 的 WebUI 管理 B 的 nginx 等）：

```java
// 在节点上执行命令
ExecResult r = ctx.exec().exec(nodeId, "nginx -t", 5000);
if (r.success()) ctx.exec().exec(nodeId, "nginx -s reload", 5000);

// 读写节点文件
ctx.files().writeFile(nodeId, "/etc/nginx/nginx.conf", content);

// 管理节点应用
ctx.apps().startApp(nodeId, appId);
```

## 9. 发布

- 设置正确的 `dependencies`（硬依赖，缺失不加载）与 `soft-dependencies`（软依赖，存在则优先加载）
- 独立类加载器**子优先**：可在 jar 内内嵌第三方依赖；`im.xz.cn.lingconsole.addon.*` 包强制用宿主版本
- 权限：公开接口显式传 `AddonContext.PUBLIC`

## 10. 调试

- `ctx.logger().info/warn/error/debug("msg {}", arg)` 输出到宿主日志（前缀 `[插件名]`）
- 插件加载/启用失败会显示 `[ERR]` 与错误信息（`/addons` 页与控制台 `addons` 指令）
