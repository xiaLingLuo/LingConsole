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
    id("com.gradleup.shadow") version "9.6.1" apply false
}

allprojects {
    group = "im.xz.cn"
    version = "1.1.86"

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    dependencies {
        testImplementation(platform("org.junit:junit-bom:6.0.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
    }

    tasks.withType<Jar> {
        manifest {
            attributes["Implementation-Title"] = project.name
            attributes["Implementation-Version"] = project.version
        }
    }
}


tasks.jar {
    enabled = false
}


val collectJar = tasks.register<Copy>("collectJar") {
    dependsOn(project(":lingconsole-app").tasks.named("shadowJar"))
    from(project(":lingconsole-app").tasks.named("shadowJar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "LingConsole.jar" }
}

val collectApiJar = tasks.register<Copy>("collectApiJar") {
    dependsOn(project(":lingconsole-api").tasks.named("jar"))
    from(project(":lingconsole-api").tasks.named("jar")) {
        rename { "lingconsole-api.jar" }
    }
    into(layout.buildDirectory.dir("libs"))
}

tasks.named("build") {
    dependsOn(collectJar, collectApiJar)
}
