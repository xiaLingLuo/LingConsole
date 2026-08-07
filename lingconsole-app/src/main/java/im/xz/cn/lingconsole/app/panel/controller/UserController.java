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
package im.xz.cn.lingconsole.app.panel.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import im.xz.cn.lingconsole.app.panel.exception.ApiException;
import im.xz.cn.lingconsole.app.panel.middleware.AuthMiddleware;
import im.xz.cn.lingconsole.app.panel.middleware.PermissionMiddleware;
import im.xz.cn.lingconsole.app.panel.model.PermissionGroup;
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.app.panel.repository.UserGroupRepository;
import im.xz.cn.lingconsole.app.panel.service.LogService;
import im.xz.cn.lingconsole.app.panel.service.PermissionService;
import im.xz.cn.lingconsole.app.panel.service.UserService;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import im.xz.cn.lingconsole.common.permission.Permissions;
import im.xz.cn.lingconsole.common.util.ErrorMessageUtil;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class UserController {

    private final UserService userService;
    private final PermissionService permissionService;
    private final UserGroupRepository userGroupRepository;
    private final LogService logService;
    private final im.xz.cn.lingconsole.app.panel.service.SessionService sessionService;

    public UserController(UserService userService, PermissionService permissionService,
                          UserGroupRepository userGroupRepository,
                          LogService logService,
                          im.xz.cn.lingconsole.app.panel.service.SessionService sessionService) {
        this.userService = userService;
        this.permissionService = permissionService;
        this.userGroupRepository = userGroupRepository;
        this.logService = logService;
        this.sessionService = sessionService;
    }

    public void register(RoutesConfig routes, String prefix) {
        routes.get(prefix + "/users", this::list);
        routes.post(prefix + "/users", this::create);
        routes.put(prefix + "/users/{id}", this::update);
        routes.delete(prefix + "/users/{id}", this::delete);

        
        routes.get(prefix + "/users/{id}/permissions", this::permissions);
        routes.put(prefix + "/users/{id}/groups", this::setGroups);
    }

    
    private void list(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.USER_MANAGE);
        java.util.List<User> users = new java.util.ArrayList<>();
        users.add(userService.rootUser());
        users.addAll(userService.listUsers());
        ctx.json(ApiResponse.ok(users.stream().map(this::safeUser).toList()));
    }

    
    private void create(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.USER_MANAGE);
        User operator = AuthMiddleware.currentUser(ctx);
        UserRequest req = ctx.bodyAsClass(UserRequest.class);
        if (req.username() == null || req.username().isBlank()) {
            throw ApiException.badRequest("用户名不能为空");
        }
        String err = im.xz.cn.lingconsole.common.util.PasswordPolicy.validate(
                req.password(), null, req.username().trim());
        if (err != null) {
            throw ApiException.badRequest(err);
        }
        try {
            User user = userService.createUser(req.username().trim(), req.password());
            if (req.groupIds() != null && !req.groupIds().isEmpty()) {
                PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
                userGroupRepository.setGroups(user.getId(), req.groupIds());
            }
            logService.record(operator.getId(), "user.create", user.getUsername(), "创建用户", ctx.ip());
            ctx.json(ApiResponse.ok(safeUser(user)));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(ErrorMessageUtil.friendly(e));
        }
    }

    
    private void update(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.USER_MANAGE);
        User operator = AuthMiddleware.currentUser(ctx);
        String id = ctx.pathParam("id");
        UserRequest req = ctx.bodyAsClass(UserRequest.class);
        if (im.xz.cn.lingconsole.app.panel.model.RootAccount.ROOT_ID.equals(id)) {
            throw ApiException.badRequest("不可修改 root 账户, root 密码仅可由 root 本人修改");
        }
        User target = userService.findById(id);
        if (target == null) {
            throw ApiException.notFound("用户不存在");
        }
        boolean passwordChanged = req.password() != null && !req.password().isBlank();
        if (passwordChanged) {
            String err = im.xz.cn.lingconsole.common.util.PasswordPolicy.validate(
                    req.password(), null, req.username() == null ? target.getUsername() : req.username());
            if (err != null) {
                throw ApiException.badRequest(err);
            }
        }
        try {
            User user = userService.updateUser(id, req.username(), req.password());
            if (passwordChanged) {
                sessionService.logoutAllForUser(id);
            }
            if (req.groupIds() != null) {
                PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
                userGroupRepository.setGroups(id, req.groupIds());
                sessionService.logoutAllForUser(id);
            }
            logService.record(operator.getId(), "user.update", user.getUsername(), "更新用户", ctx.ip());
            ctx.json(ApiResponse.ok(safeUser(user)));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(ErrorMessageUtil.friendly(e));
        }
    }

    
    private void delete(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.USER_MANAGE);
        User operator = AuthMiddleware.currentUser(ctx);
        String id = ctx.pathParam("id");
        try {
            userService.deleteUser(id);
            sessionService.logoutAllForUser(id);
            userGroupRepository.setGroups(id, List.of());
            logService.record(operator.getId(), "user.delete", id, "删除用户", ctx.ip());
            ctx.json(ApiResponse.ok());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(ErrorMessageUtil.friendly(e));
        }
    }

    
    private void permissions(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
        String id = ctx.pathParam("id");
        User user = userService.findById(id);
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }
        Set<String> perms = permissionService.permissionsOf(id);
        List<Map<String, String>> groups = userGroupRepository.findGroupsByUser(id).stream()
                .map(g -> Map.of("id", g.getId(), "name", g.getName()))
                .toList();
        ctx.json(ApiResponse.ok(Map.of(
                "userId", id,
                "permissions", perms,
                "groups", groups)));
    }

    
    private void setGroups(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
        String id = ctx.pathParam("id");
        if (im.xz.cn.lingconsole.app.panel.model.RootAccount.ROOT_ID.equals(id)) {
            throw ApiException.badRequest("不可修改 root 账户的权限组");
        }
        GroupRequest req = ctx.bodyAsClass(GroupRequest.class);
        userGroupRepository.setGroups(id, req.groupIds() == null ? List.of() : req.groupIds());
        sessionService.logoutAllForUser(id);
        ctx.json(ApiResponse.ok());
    }

    private Map<String, Object> safeUser(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("role", user.getRole().value());
        map.put("roleName", user.getRole().roleName());
        List<String> groupNames = userGroupRepository.findGroupsByUser(user.getId()).stream()
                .map(PermissionGroup::getName)
                .toList();
        map.put("groups", groupNames);
        map.put("hasGroups", !groupNames.isEmpty());
        map.put("createdAt", user.getCreatedAt());
        map.put("updatedAt", user.getUpdatedAt());
        return map;
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record UserRequest(@JsonProperty("username") String username,
                              @JsonProperty("password") String password,
                              @JsonProperty("groupIds") List<String> groupIds) {
    }

    public record GroupRequest(@JsonProperty("groupIds") List<String> groupIds) {
    }
}
