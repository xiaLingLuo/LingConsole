# 插件开发教程

本教程面向 LingConsole 1.2.9。可运行参考位于仓库 `exampleAddon/`。

## 1. 准备

先构建宿主 API：

```bash
gradlew.bat build
```

需要 JDK 25、`build/libs/lingconsole-api.jar`，以及编译路由处理器所需的 Javalin 7 API。依赖应使用 `compileOnly`，不要把宿主 API 打入插件。

## 2. 项目结构

```text
myaddon/
|-- addon.toml
|-- build.gradle.kts
`-- src/main/java/com/example/MyAddon.java
```

`addon.toml`：

```toml
name = "myaddon"
version = "1.0.0"
main = "com.example.MyAddon"
author = "You"
description = "My first addon"
api-version = "1.2.9"
```

## 3. 主类

```java
package com.example;

import im.xz.cn.lingconsole.addon.*;
import java.util.Map;

public final class MyAddon implements Addon {
    private AddonContext context;

    @Override
    public void onLoad(AddonContext context) {
        this.context = context;

        context.registerPermission("status.read", "Read addon status");
        context.config().define("message", ConfigType.STRING,
                "Message", "Returned by the status endpoint", "hello");

        context.registerPanelRoute(AddonRouteMethod.GET, "/status", h ->
                h.json(Map.of("message",
                        context.config().getString("message", "hello"))),
                "status.read");

        context.registerDaemonRoute(AddonRouteMethod.GET, "/health", h ->
                h.json(Map.of("status", "ok")));

        context.registerSocketEvent("/panel", "myaddon:status", "status.read",
                (connection, event, data) ->
                        connection.emit(event, Map.of("status", "ok")));

        context.registerCommand("status", (command, args, sender) ->
                sender.sendMessage("myaddon is enabled"));
    }

    @Override
    public void onEnable(AddonContext context) {
        context.logger().info("enabled");
    }

    @Override
    public void onDisable() {
        // Close resources created directly by the addon here.
    }
}
```

Panel 路由和 Socket 事件应按最小权限原则声明权限。`AddonContext.PUBLIC` 仍要求用户已登录，只是跳过额外权限键。Daemon 路由由 Daemon Key 保护，不承载 Panel 用户身份。

## 4. Gradle 配置

```kotlin
plugins { java }

repositories { mavenCentral() }

dependencies {
    compileOnly(files("libs/lingconsole-api.jar", "libs/javalin.jar"))
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    from("addon.toml")
}
```

第三方库可打入插件 JAR；不要打入 LingConsole API。确保 `addon.toml` 位于最终 JAR 根目录。

## 5. 构建与部署

```bash
gradle clean build
cp build/libs/myaddon.jar /lingConsole/addons/
```

首次部署后重启 LingConsole。后续可以替换 JAR，并由拥有 `lingconsole.permission.assign` 的用户在插件页执行热重载。热重载会调用旧实例 `onDisable`、注销宿主资源、关闭类加载器，再创建新上下文和实例。

## 6. 验证

- Panel 路由：登录后请求 `/api/addon/myaddon/status`。
- Daemon 路由：携带 `X-LingConsole-Key` 请求 `/consoleapi/addon/myaddon/health`。
- Panel Socket：连接 `/panel` 后发送 `myaddon:status`，当前用户必须拥有 `myaddon.status.read`。
- 控制台：输入 `myaddon:status`。
- 插件页：确认状态为 `ENABLED`，配置项可保存并热重载。

## 7. 远端服务

```java
var nodes = context.nodes().listNodes();
var result = context.exec().exec(nodeId, "nginx -t", 5000);
if (result.success()) {
    context.apps().restartApp(nodeId, appId);
}
context.logs().record("nginx.reload", nodeId, "configuration reloaded");
```

服务接口代表宿主权限，不自动应用发起请求的浏览器用户权限。暴露这些能力的插件入口必须进行权限保护和参数校验，不要把用户输入直接拼入命令、路径、目标 URL 或响应头。

## 8. 发布检查

- 使用唯一、稳定、只含规范字符的插件名。
- 填写准确的 `api-version`、硬依赖和软依赖。
- 所有 Panel 路由、代理和 Socket 事件使用最小权限。
- 敏感代理头只按需加入白名单。
- 调度任务和自行创建的线程、连接、文件句柄在 `onDisable` 中关闭。
- 不在日志、配置响应或异常中泄露密码、令牌和 Daemon Key。
- 在完整宿主和 only-daemon 模式下分别验证实际使用的能力。

API 详细说明见 [插件 API](addon.md)。
