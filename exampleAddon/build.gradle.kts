// LingConsole - A Server WebUI control panel
// Copyright (C) 2026  XIAZHIRUI HUANG
// 
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
// 
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
plugins {
    java
}

repositories {
    mavenCentral()
}

// 编译期依赖: 仅需 API 包 + Javalin (不打包进插件)
val apiJar = file("libs/lingconsole-api.jar")
val javalinJar = file("libs/javalin.jar")

dependencies {
    compileOnly(files(apiJar, javalinJar))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveFileName.set("exampleAddon.jar")
    // 将 addon.toml 描述文件置于 JAR 根目录
    from("addon.toml")
}
