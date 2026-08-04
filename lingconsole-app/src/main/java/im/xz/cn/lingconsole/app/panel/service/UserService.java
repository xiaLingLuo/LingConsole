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

import im.xz.cn.lingconsole.app.panel.model.RootAccount;
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.app.panel.repository.RootAccountRepository;
import im.xz.cn.lingconsole.app.panel.repository.UserRepository;
import im.xz.cn.lingconsole.common.config.Constants;
import im.xz.cn.lingconsole.common.model.UserRole;
import im.xz.cn.lingconsole.common.util.Argon2Util;
import im.xz.cn.lingconsole.common.util.IdUtil;


public class UserService {

    private final UserRepository userRepository;
    private final RootAccountRepository rootAccountRepository;
    private volatile boolean singleUserMode;
    private volatile PermissionService permissionService;

    public UserService(UserRepository userRepository, RootAccountRepository rootAccountRepository) {
        this.userRepository = userRepository;
        this.rootAccountRepository = rootAccountRepository;
    }

    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void setSingleUserMode(boolean singleUserMode) {
        this.singleUserMode = singleUserMode;
    }
    public boolean isSingleUserMode() {
        return singleUserMode;
    }

    
    public boolean isDatabaseTampered() {
        if (rootAccountRepository.count() > 1) {
            return true;
        }
        return userRepository.findByUsernameIgnoreCase("ling").isPresent()
                || userRepository.findByUsernameIgnoreCase("root").isPresent();
    }

    private static boolean isReservedUsername(String username) {
        if (username == null) {
            return false;
        }
        String u = username.trim().toLowerCase();
        return "ling".equals(u) || "root".equals(u) || "lingconsole".equals(u);
    }

    public static boolean isRootUsername(String username) {
        if (username == null) {
            return false;
        }
        String u = username.trim().toLowerCase();
        return "ling".equals(u) || "root".equals(u);
    }

    public User login(String username, String password) {
        if (singleUserMode && !isRootUsername(username)) {
            return null;
        }
        if (isRootUsername(username)) {
            RootAccount root = rootAccountRepository.findRoot().orElse(null);
            if (root != null && Argon2Util.verify(password, root.getPassword())) {
                return rootUser();
            }
            return null;
        }
        return userRepository.findByUsername(username)
                .filter(u -> Argon2Util.verify(password, u.getPassword()))
                .filter(u -> !isBanned(u))
                .orElse(null);
    }

    
    private boolean isBanned(User user) {
        if (permissionService == null) {
            return false;
        }
        try {
            return permissionService.permissionsOf(user.getId())
                    .contains(im.xz.cn.lingconsole.common.permission.Permissions.USER_BANNED);
        } catch (Exception e) {
            return false;
        }
    }

    public User findById(String id) {
        if (RootAccount.ROOT_ID.equals(id)) {
            return rootUser();
        }
        return userRepository.findById(id).orElse(null);
    }

    
    public User rootUser() {
        long now = System.currentTimeMillis() / 1000;
        User user = new User();
        user.setId(RootAccount.ROOT_ID);
        user.setUsername(Constants.DEFAULT_USERNAME);
        user.setRole(UserRole.ROOT);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }

    public boolean changeRootPassword(String oldPassword, String newPassword) {
        RootAccount root = rootAccountRepository.findRoot().orElse(null);
        if (root == null || !Argon2Util.verify(oldPassword, root.getPassword())) {
            return false;
        }
        rootAccountRepository.updatePassword(Argon2Util.hash(newPassword));
        return true;
    }

    public boolean changeUserPassword(String userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        if (!Argon2Util.verify(oldPassword, user.getPassword())) {
            return false;
        }
        userRepository.updatePassword(userId, Argon2Util.hash(newPassword));
        return true;
    }

    
    
    

    
    public String firstLaunchInit() {
        if (rootAccountRepository.count() > 0) {
            return null;
        }
        String password = IdUtil.randomPassword();
        String hash = Argon2Util.hash(password);
        long now = System.currentTimeMillis() / 1000;

        RootAccount root = new RootAccount();
        root.setId(RootAccount.ROOT_ID);
        root.setUsername(Constants.DEFAULT_USERNAME);
        root.setPassword(hash);
        root.setCreatedAt(now);
        root.setUpdatedAt(now);
        rootAccountRepository.insert(root);
        return password;
    }

    
    
    

    public java.util.List<User> listUsers() {
        return userRepository.findAll();
    }

    
    public User createUser(String username, String password) {
        if (isReservedUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        long now = System.currentTimeMillis() / 1000;
        User user = new User();
        user.setId(IdUtil.uuid());
        user.setUsername(username);
        user.setPassword(Argon2Util.hash(password));
        user.setRole(UserRole.NORMAL);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.insert(user);
        return user;
    }

    public User updateUser(String id, String username, String password) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return null;
        }
        if (username != null && !username.isBlank()) {
            if (isReservedUsername(username)) {
                throw new IllegalArgumentException("用户名已存在: " + username);
            }
            user.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            user.setPassword(Argon2Util.hash(password));
        }
        user.setUpdatedAt(System.currentTimeMillis() / 1000);
        userRepository.update(user);
        return user;
    }

    public boolean deleteUser(String id) {
        if (RootAccount.ROOT_ID.equals(id)) {
            throw new IllegalArgumentException("不可删除 root 账户");
        }
        userRepository.delete(id);
        return true;
    }
}
