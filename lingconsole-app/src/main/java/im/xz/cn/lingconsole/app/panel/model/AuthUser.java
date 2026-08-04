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
package im.xz.cn.lingconsole.app.panel.model;

import im.xz.cn.lingconsole.common.model.UserRole;
import im.xz.cn.lingconsole.common.permission.PermissionMatcher;

import java.util.HashSet;
import java.util.Set;


public record AuthUser(User user, Set<String> permissions) {

    public AuthUser(User user, Set<String> permissions) {
        this.user = user;
        this.permissions = permissions == null ? new HashSet<>() : new HashSet<>(permissions);
    }

    public boolean isRoot() {
        return user.getRole() == UserRole.ROOT;
    }

    public boolean hasPermission(String key) {
        for (String p : permissions) {
            if (PermissionMatcher.matches(p, key)) {
                return true;
            }
        }
        return false;
    }
}
