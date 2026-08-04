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
package im.xz.cn.lingconsole.common.permission;


public final class PermissionMatcher {

    private PermissionMatcher() {
    }

    
    public static boolean matches(String pattern, String key) {
        if (pattern == null || key == null) {
            return false;
        }
        if (pattern.equals(key)) {
            return true;
        }
        if (pattern.equals("*")) {
            return true;
        }
        if (pattern.endsWith(".*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            if (key.length() > prefix.length() && key.startsWith(prefix)
                    && key.charAt(prefix.length()) == '.') {
                return true;
            }
        }
        return matchSegments(pattern.split("\\."), key.split("\\."));
    }

    private static boolean matchSegments(String[] pattern, String[] key) {
        if (pattern.length != key.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if ("*".equals(pattern[i])) {
                continue;
            }
            if (!pattern[i].equals(key[i])) {
                return false;
            }
        }
        return true;
    }
}
