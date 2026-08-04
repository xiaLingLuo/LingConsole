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
package im.xz.cn.lingconsole.common.util;

import java.nio.file.Path;
import java.nio.file.Paths;


public final class PathUtil {

    private PathUtil() {
    }

    
    public static Path sanitize(String baseDir, String userPath) {
        Path base = Paths.get(baseDir).toAbsolutePath().normalize();
        Path resolved = base.resolve(userPath).normalize().toAbsolutePath();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("Path traversal detected: " + userPath);
        }
        return resolved;
    }

    
    public static boolean isUnder(String baseDir, String targetPath) {
        Path base = Paths.get(baseDir).toAbsolutePath().normalize();
        Path target = Paths.get(targetPath).toAbsolutePath().normalize();
        return target.startsWith(base);
    }

    
    public static String safeFileName(Path p) {
        if (p == null || p.getFileName() == null) {
            return "file";
        }
        String name = p.getFileName().toString();
        return name.replaceAll("[\\r\\n\"]", "_");
    }
}
