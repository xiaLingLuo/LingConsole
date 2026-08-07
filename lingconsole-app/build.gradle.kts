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
    application
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":lingconsole-common"))
    implementation(project(":lingconsole-daemon"))
    implementation(project(":lingconsole-api"))

    implementation("io.javalin:javalin:7.2.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    implementation("org.thymeleaf:thymeleaf:3.1.5.RELEASE")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("org.yaml:snakeyaml:2.6")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    implementation("ch.qos.logback:logback-classic:1.6.1")
}

application {
    mainClass = "im.xz.cn.lingconsole.app.LingConsoleApp"
}

tasks.shadowJar {
    archiveBaseName.set("LingConsole")
    archiveClassifier.set("")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
    
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("module-info.class", "META-INF/versions/*/module-info.class")
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*")
    exclude("META-INF/maven/**/pom.properties", "META-INF/maven/**/pom.xml")
    exclude("META-INF/native-image/**")
    
    exclude("**/*.kotlin_metadata", "**/*.kotlin_module", "**/*.kotlin_builtins")
    exclude("javax/annotation/**", "org/jetbrains/annotations/**")
    
    exclude("**/*.d.ts")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
