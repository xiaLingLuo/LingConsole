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
package im.xz.cn.lingconsole.common.addon;

import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;


public final class AddonDescriptorLoader {

    private static final String DESCRIPTOR_FILE = "addon.toml";

    private AddonDescriptorLoader() {
    }

    public static AddonDescriptor load(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry(DESCRIPTOR_FILE);
            if (entry == null) {
                throw new IllegalArgumentException("插件缺少 addon.toml: " + jarPath);
            }
            String content;
            try (var in = jar.getInputStream(entry)) {
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return parse(content, jarPath);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取插件 " + jarPath + ": " + e.getMessage(), e);
        }
    }

    static AddonDescriptor parse(String content, Path source) {
        TomlParseResult result = Toml.parse(content);
        if (result.hasErrors()) {
            throw new IllegalArgumentException("addon.toml 解析失败: " + source
                    + " -> " + result.errors().getFirst());
        }
        String name = result.getString("name");
        String version = result.getString("version");
        String main = result.getString("main");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("addon.toml 缺少 name: " + source);
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("addon.toml 缺少 version: " + source);
        }
        if (main == null || main.isBlank()) {
            throw new IllegalArgumentException("addon.toml 缺少 main: " + source);
        }

        List<String> dependencies = new ArrayList<>();
        TomlArray arr = result.getArray("dependencies");
        if (arr != null) {
            arr.toList().forEach(o -> dependencies.add(String.valueOf(o)));
        }
        List<String> softDependencies = new ArrayList<>();
        TomlArray softArr = result.getArray("soft-dependencies");
        if (softArr != null) {
            softArr.toList().forEach(o -> softDependencies.add(String.valueOf(o)));
        }

        return new AddonDescriptor(
                name.trim(),
                version.trim(),
                main.trim(),
                result.getString("author"),
                result.getString("description"),
                result.getString("api-version"),
                dependencies,
                softDependencies);
    }
}
