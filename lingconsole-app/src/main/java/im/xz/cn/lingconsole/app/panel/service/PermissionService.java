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
package im.xz.cn.lingconsole.app.panel.service;

import im.xz.cn.lingconsole.app.panel.model.AuthUser;
import im.xz.cn.lingconsole.app.panel.model.PermissionGroup;
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.app.panel.repository.UserGroupRepository;
import im.xz.cn.lingconsole.app.panel.repository.UserRepository;
import im.xz.cn.lingconsole.common.permission.Permissions;

import java.util.HashSet;
import java.util.Set;


public class PermissionService {

    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;

    public PermissionService(UserRepository userRepository,
                             UserGroupRepository userGroupRepository) {
        this.userRepository = userRepository;
        this.userGroupRepository = userGroupRepository;
    }

    
    public Set<String> permissionsOf(String userId) {
        if (im.xz.cn.lingconsole.app.panel.model.RootAccount.ROOT_ID.equals(userId)) {
            return Permissions.ROOT_ALL;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Set.of();
        }
        Set<String> perms = new HashSet<>();
        for (PermissionGroup group : userGroupRepository.findGroupsByUser(userId)) {
            perms.addAll(group.getPermissions());
        }
        return perms;
    }

    
    public AuthUser buildAuthUser(User user) {
        if (user == null) {
            return null;
        }
        Set<String> perms = permissionsOf(user.getId());
        return new AuthUser(user, perms);
    }

    
    public boolean hasPermission(String userId, String key) {
        for (String p : permissionsOf(userId)) {
            if (im.xz.cn.lingconsole.common.permission.PermissionMatcher.matches(p, key)) {
                return true;
            }
        }
        return false;
    }
}
