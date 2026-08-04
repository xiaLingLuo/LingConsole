# exampleAddon

LingConsole 示例插件项目。**独立项目**，可从主项目目录整体移出后独立编译为插件 JAR，放入任意 LingConsole 实例的 `addons/` 目录并重启生效。

## 编译

### 方式一: 独立脚本 (推荐, 零依赖)

`libs/` 已包含 `lingconsole-api.jar` 与 `javalin.jar` (API 包 + Javalin 编译期依赖):

```bash
# Windows
build.bat

# Linux / macOS
chmod +x build.sh && ./build.sh
```

产物: `exampleAddon.jar`

### 方式二: Gradle

```bash
gradle jar     # 或 gradlew jar (若配置 wrapper)
# 产物: build/libs/exampleAddon.jar
```

## 安装

将 `exampleAddon.jar` 放入 LingConsole 数据目录的 `addons/` 文件夹并重启:

```
/lingConsole/addons/exampleAddon.jar
```