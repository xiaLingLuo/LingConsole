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
package im.xz.cn.lingconsole.common.config;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;


public abstract class TomlConfig {

    protected final TomlParseResult result;

    protected TomlConfig(TomlParseResult result) {
        this.result = result;
    }

    protected TomlParseResult result() {
        return result;
    }

    public static TomlParseResult parse(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("配置文件不存在: " + path);
        }
        TomlParseResult r = Toml.parse(path);
        if (r.hasErrors()) {
            throw new IOException("TOML 解析错误: " + r.errors());
        }
        return r;
    }

    protected String str(String key, String def) {
        if (result.contains(key)) {
            String v = result.getString(key);
            return v != null ? v : def;
        }
        return def;
    }

    protected boolean bool(String key, boolean def) {
        return result.contains(key) ? Boolean.TRUE.equals(result.getBoolean(key)) : def;
    }

    protected int intVal(String key, int def) {
        if (result.contains(key)) {
            Long v = result.getLong(key);
            return v != null ? v.intValue() : def;
        }
        return def;
    }

    protected long longVal(String key, long def) {
        if (result.contains(key)) {
            Long v = result.getLong(key);
            return v != null ? v : def;
        }
        return def;
    }

    protected double doubleVal(String key, double def) {
        if (result.contains(key)) {
            Double v = result.getDouble(key);
            return v != null ? v : def;
        }
        return def;
    }

    protected List<String> strList(String key, List<String> def) {
        if (result.contains(key)) {
            Object obj = result.get(key);
            if (obj instanceof org.tomlj.TomlArray arr) {
                return arr.toList().stream().map(String::valueOf).toList();
            }
            if (obj instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        }
        return def;
    }

    protected Map<String, String> strMap(String key) {
        Map<String, String> resultMap = new java.util.LinkedHashMap<>();
        if (result.contains(key)) {
            Object obj = result.get(key);
            if (obj instanceof org.tomlj.TomlTable table) {
                for (String k : table.keySet()) {
                    Object v = table.get(k);
                    resultMap.put(k, v == null ? "" : String.valueOf(v));
                }
            } else if (obj instanceof Map<?, ?> map) {
                map.forEach((k, v) -> resultMap.put(String.valueOf(k), String.valueOf(v)));
            }
        }
        return resultMap;
    }

    public static void ensureDefaultFile(Path path, String defaultContent) throws IOException {
        if (!Files.exists(path)) {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, defaultContent, java.nio.charset.StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignore) {
                assert true;
            }
        }
    }
}
