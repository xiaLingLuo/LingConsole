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
import im.xz.cn.lingconsole.app.panel.repository.PermissionGroupRepository;
import im.xz.cn.lingconsole.app.panel.service.LogService;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import im.xz.cn.lingconsole.common.permission.Permissions;
import im.xz.cn.lingconsole.common.util.IdUtil;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class PermissionGroupController {

    private final PermissionGroupRepository groupRepository;
    private final LogService logService;
    private final im.xz.cn.lingconsole.app.panel.service.NodePermissionIndex nodePermissionIndex;

    public PermissionGroupController(PermissionGroupRepository groupRepository, LogService logService,
                                     im.xz.cn.lingconsole.app.panel.service.NodePermissionIndex nodePermissionIndex) {
        this.groupRepository = groupRepository;
        this.logService = logService;
        this.nodePermissionIndex = nodePermissionIndex;
    }

    public void register(RoutesConfig routes, String prefix) {
        routes.get(prefix + "/permission-groups", this::list);
        routes.post(prefix + "/permission-groups", this::create);
        routes.put(prefix + "/permission-groups/{id}", this::update);
        routes.delete(prefix + "/permission-groups/{id}", this::delete);
        routes.get(prefix + "/permission-keys", this::keys);
    }

    
    private void list(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
        ctx.json(ApiResponse.ok(groupRepository.findAll()));
    }

    
    private void create(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
        GroupRequest req = ctx.bodyAsClass(GroupRequest.class);
        if (req.name() == null || req.name().isBlank()) {
            throw ApiException.badRequest("权限组名称不能为空");
        }
        Set<String> perms = sanitize(req.permissions());
        PermissionGroup group = new PermissionGroup();
        group.setId(IdUtil.uuid());
        group.setGroupId(normalizeGroupId(req.groupId()));
        group.setName(req.name().trim());
        group.setDescription(req.description());
        group.setPermissions(perms);
        group.setCreatedAt(System.currentTimeMillis() / 1000);
        try {
            groupRepository.insert(group);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("权限组名称已存在");
        }
        logService.record(AuthMiddleware.currentUser(ctx).getId(), "permissionGroup.create", group.getName(), "创建权限组", ctx.ip());
        ctx.json(ApiResponse.ok(group));
    }

    
    private void update(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
        String id = ctx.pathParam("id");
        PermissionGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) {
            throw ApiException.notFound("权限组不存在");
        }
        GroupRequest req = ctx.bodyAsClass(GroupRequest.class);
        if (req.name() != null && !req.name().isBlank()) {
            group.setName(req.name().trim());
        }
        if (req.groupId() != null && !req.groupId().isBlank()) {
            group.setGroupId(normalizeGroupId(req.groupId()));
        }
        group.setDescription(req.description());
        if (req.permissions() != null) {
            group.setPermissions(sanitize(req.permissions()));
        }
        groupRepository.update(group);
        logService.record(AuthMiddleware.currentUser(ctx).getId(), "permissionGroup.update", group.getName(), "更新权限组", ctx.ip());
        ctx.json(ApiResponse.ok(group));
    }

    
    private String normalizeGroupId(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            throw ApiException.badRequest("权限组 ID 不能为空");
        }
        String gid = groupId.trim().toLowerCase();
        if (!gid.matches("[a-z]+")) {
            throw ApiException.badRequest("权限组 ID 仅允许小写英文字母");
        }
        return gid;
    }

    
    private void delete(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
        String id = ctx.pathParam("id");
        groupRepository.delete(id);
        logService.record(AuthMiddleware.currentUser(ctx).getId(), "permissionGroup.delete", id, "删除权限组", ctx.ip());
        ctx.json(ApiResponse.ok());
    }

    
    private void keys(Context ctx) {
        PermissionMiddleware.requirePermission(ctx, Permissions.PERMISSION_ASSIGN);
        java.util.Set<String> all = new java.util.LinkedHashSet<>(Permissions.ALL);
        java.util.Set<String> grantable = new java.util.LinkedHashSet<>(Permissions.GRANTABLE);
        Map<String, String> labels = permissionLabels();

        
        for (String global : new String[]{
                "lingconsole.node.read.*", "lingconsole.node.write.*",
                "lingconsole.file.node.*", "lingconsole.terminal.node.*",
                "lingconsole.monitor.read.*",
                "lingconsole.app.read.*", "lingconsole.app.write.*",
                "lingconsole.app.advanced.*", "lingconsole.file.app.*",
                "lingconsole.terminal.app.*"}) {
            all.add(global);
            grantable.add(global);
        }
        labels.put("lingconsole.node.read.*", "节点查看 (全部节点)");
        labels.put("lingconsole.node.write.*", "节点管理 (全部节点)");
        labels.put("lingconsole.file.node.*", "节点文件管理 (全部节点)");
        labels.put("lingconsole.terminal.node.*", "节点终端 (全部节点)");
        labels.put("lingconsole.monitor.read.*", "节点监控 (全部节点)");
        labels.put("lingconsole.app.read.*", "应用查看 (全部节点)");
        labels.put("lingconsole.app.write.*", "应用管理 (全部节点)");
        labels.put("lingconsole.app.advanced.*", "应用高级配置 (全部节点)");
        labels.put("lingconsole.file.app.*", "应用文件管理 (全部节点)");
        labels.put("lingconsole.terminal.app.*", "应用终端 (全部节点)");

        for (im.xz.cn.lingconsole.app.panel.model.Node node : nodePermissionIndex.nodes()) {
            String nid = node.getId();
            all.add("lingconsole.node.read." + nid);
            all.add("lingconsole.node.write." + nid);
            all.add("lingconsole.file.node." + nid);
            all.add("lingconsole.terminal.node." + nid);
            all.add("lingconsole.monitor.read." + nid);
            grantable.add("lingconsole.node.write." + nid);
            grantable.add("lingconsole.file.node." + nid);
            grantable.add("lingconsole.terminal.node." + nid);
            grantable.add("lingconsole.monitor.read." + nid);
            labels.put("lingconsole.node.read." + nid, "节点查看: " + node.getName());
            labels.put("lingconsole.node.write." + nid, "节点管理: " + node.getName() + " ⚠高风险");
            labels.put("lingconsole.file.node." + nid, "节点文件管理: " + node.getName() + " ⚠高风险");
            labels.put("lingconsole.terminal.node." + nid, "节点终端: " + node.getName() + " ⚠高风险");
            labels.put("lingconsole.monitor.read." + nid, "节点监控: " + node.getName());
            for (im.xz.cn.lingconsole.app.panel.service.NodePermissionIndex.AppRef app : nodePermissionIndex.appsOf(nid)) {
                String aid = app.id();
                String scope = node.getName() + " / " + app.name();
                all.add("lingconsole.app.read." + nid + "." + aid);
                all.add("lingconsole.app.write." + nid + "." + aid);
                all.add("lingconsole.app.advanced." + nid + "." + aid);
                all.add("lingconsole.file.app." + nid + "." + aid);
                all.add("lingconsole.terminal.app." + nid + "." + aid);
                grantable.add("lingconsole.app.write." + nid + "." + aid);
                grantable.add("lingconsole.app.advanced." + nid + "." + aid);
                grantable.add("lingconsole.file.app." + nid + "." + aid);
                grantable.add("lingconsole.terminal.app." + nid + "." + aid);
                labels.put("lingconsole.app.read." + nid + "." + aid, "应用查看: " + scope);
                labels.put("lingconsole.app.write." + nid + "." + aid, "应用管理: " + scope + " ⚠高风险");
                labels.put("lingconsole.app.advanced." + nid + "." + aid, "应用高级配置: " + scope + " ⚠高风险");
                labels.put("lingconsole.file.app." + nid + "." + aid, "应用文件管理: " + scope + " ⚠高风险");
                labels.put("lingconsole.terminal.app." + nid + "." + aid, "应用终端: " + scope + " ⚠高风险");
            }
        }
        all.addAll(im.xz.cn.lingconsole.common.permission.PluginPermissionRegistry.allKeys());
        labels.putAll(im.xz.cn.lingconsole.common.permission.PluginPermissionRegistry.all());
        ctx.json(ApiResponse.ok(Map.of(
                "all", all,
                "grantable", grantable,
                "labels", labels)));
    }

    private Map<String, String> permissionLabels() {
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        labels.put(Permissions.USER_MANAGE, "用户管理");
        labels.put(Permissions.NODE_READ, "节点查看");
        labels.put(Permissions.NODE_WRITE, "节点管理");
        labels.put(Permissions.APP_READ, "应用查看");
        labels.put(Permissions.APP_WRITE, "应用管理");
        labels.put(Permissions.APP_ADVANCED, "应用高级配置");
        labels.put(Permissions.FILE_NODE, "节点文件管理");
        labels.put(Permissions.FILE_APP, "应用文件管理");
        labels.put(Permissions.TERMINAL_NODE, "节点终端");
        labels.put(Permissions.TERMINAL_APP, "应用终端");
        labels.put(Permissions.MONITOR_READ, "节点监控");
        labels.put(Permissions.SYSTEM_STATUS, "查看运行状态");
        labels.put(Permissions.LOG_READ, "操作日志");
        labels.put(Permissions.PACKAGES, "包管理器");
        labels.put(Permissions.USER_BANNED, "封禁用户");
        labels.put(Permissions.PERMISSION_ASSIGN, "权限分配");
        return labels;
    }

    
    private Set<String> sanitize(Set<String> permissions) {
        Set<String> result = new HashSet<>();
        if (permissions != null) {
            for (String p : permissions) {
                if (p != null && !p.isBlank()) {
                    result.add(p.trim());
                }
            }
        }
        return result;
    }

    public record GroupRequest(@JsonProperty("groupId") String groupId,
                               @JsonProperty("name") String name,
                               @JsonProperty("description") String description,
                               @JsonProperty("permissions") Set<String> permissions) {
    }
}
