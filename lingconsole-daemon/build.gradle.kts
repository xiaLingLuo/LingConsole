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
dependencies {
    implementation(project(":lingconsole-common"))
    implementation(project(":lingconsole-api"))

    implementation("io.javalin:javalin:7.2.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("org.jetbrains.pty4j:pty4j:0.13.12")
    implementation("com.github.oshi:oshi-core:6.6.5")
    implementation("ch.qos.logback:logback-classic:1.6.1")
}
