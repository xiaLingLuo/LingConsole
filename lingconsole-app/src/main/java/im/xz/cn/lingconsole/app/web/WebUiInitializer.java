/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package im.xz.cn.lingconsole.app.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


public final class WebUiInitializer {

    private static final Logger log = LoggerFactory.getLogger(WebUiInitializer.class);

    private WebUiInitializer() {
    }

    public static void deploy(Path webDir) {
        try {
            Path targetDir = webDir.resolve("static");
            Files.createDirectories(targetDir);
            ClassLoader cl = WebUiInitializer.class.getClassLoader();
            URL staticUrl = cl.getResource("static");
            if (staticUrl == null) {
                log.warn("未找到内置静态资源: classpath:/static");
                return;
            }
            if ("file".equals(staticUrl.getProtocol())) {
                copyFromDirectory(Path.of(staticUrl.toURI()), targetDir);
            } else if ("jar".equals(staticUrl.getProtocol())) {
                copyFromJar(staticUrl, targetDir);
            }
            log.info("WebUI 静态资源已部署: {}", targetDir);
        } catch (Exception e) {
            log.warn("WebUI 静态资源部署失败 (将使用内置资源)", e);
        }
    }

    private static void copyFromDirectory(Path sourceDir, Path targetDir) throws IOException {
        if (!Files.exists(sourceDir)) {
            return;
        }
        try (var stream = Files.walk(sourceDir)) {
            stream.forEach(source -> {
                try {
                    Path relative = sourceDir.relativize(source);
                    Path target = targetDir.resolve(relative.toString());
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.warn("复制静态资源失败: {}", source, e);
                }
            });
        }
    }

    private static void copyFromJar(URL staticUrl, Path targetDir) throws IOException {
        JarURLConnection conn = (JarURLConnection) staticUrl.openConnection();
        try (JarFile jar = conn.getJarFile()) {
            String entryPrefix = conn.getEntryName(); 
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(entryPrefix) || entry.isDirectory()) {
                    continue;
                }
                String relative = name.substring(entryPrefix.length());
                Path target = targetDir.resolve(relative);
                Files.createDirectories(target.getParent());
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
