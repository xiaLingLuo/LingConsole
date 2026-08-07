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

    private static final String DUMMY_PASSWORD_HASH =
            "argon2id$v=19$m=65536,t=3,p=4$00112233445566778899aabbccddeeff$"
                    + "0000000000000000000000000000000000000000000000000000000000000000";

    private final UserRepository userRepository;
    private final RootAccountRepository rootAccountRepository;
    private volatile boolean singleUserMode;
    private volatile PermissionService permissionService;
    private volatile java.util.concurrent.Semaphore passwordVerificationPermits =
            new java.util.concurrent.Semaphore(2, true);
    private volatile long passwordVerificationTimeoutMillis = 250;

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

    public void configurePasswordVerification(int concurrency, long timeoutMillis) {
        passwordVerificationPermits = new java.util.concurrent.Semaphore(Math.max(1, concurrency), true);
        passwordVerificationTimeoutMillis = Math.max(1, timeoutMillis);
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
        String encoded = DUMMY_PASSWORD_HASH;
        User candidate = null;
        if (isRootUsername(username)) {
            RootAccount root = rootAccountRepository.findRoot().orElse(null);
            if (root != null) {
                encoded = root.getPassword();
                candidate = rootUser();
            }
        } else if (!singleUserMode) {
            candidate = userRepository.findByUsername(username).orElse(null);
            if (candidate != null) {
                encoded = candidate.getPassword();
            }
        }
        boolean verified = verifyPassword(password, encoded);
        return verified && candidate != null && (candidate.getRole() == UserRole.ROOT || !isBanned(candidate))
                ? candidate : null;
    }

    private boolean verifyPassword(String password, String encoded) {
        java.util.concurrent.Semaphore permits = passwordVerificationPermits;
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(passwordVerificationTimeoutMillis,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new PasswordVerificationBusyException();
            }
            return Argon2Util.verify(password, encoded);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PasswordVerificationBusyException();
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }

    public static final class PasswordVerificationBusyException extends RuntimeException {
    }

    
    public boolean isBanned(User user) {
        if (permissionService == null) {
            return true;
        }
        try {
            return permissionService.permissionsOf(user.getId())
                    .contains(im.xz.cn.lingconsole.common.permission.Permissions.USER_BANNED);
        } catch (Exception e) {
            return true;
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
        String err = im.xz.cn.lingconsole.common.util.PasswordPolicy.validate(
                newPassword, oldPassword, Constants.DEFAULT_USERNAME);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        RootAccount root = rootAccountRepository.findRoot().orElse(null);
        if (root == null || !verifyPassword(oldPassword, root.getPassword())) {
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
        String err = im.xz.cn.lingconsole.common.util.PasswordPolicy.validate(
                newPassword, oldPassword, user.getUsername());
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        if (!verifyPassword(oldPassword, user.getPassword())) {
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
        String err = im.xz.cn.lingconsole.common.util.PasswordPolicy.validate(
                password, null, username == null ? null : username.trim());
        if (err != null) {
            throw new IllegalArgumentException(err);
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
            String err = im.xz.cn.lingconsole.common.util.PasswordPolicy.validate(
                    password, null, username == null ? user.getUsername() : username);
            if (err != null) {
                throw new IllegalArgumentException(err);
            }
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
