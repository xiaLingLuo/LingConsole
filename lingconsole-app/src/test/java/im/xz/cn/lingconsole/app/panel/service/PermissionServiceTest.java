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
import im.xz.cn.lingconsole.common.permission.Permissions;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionServiceTest {

    @Test
    void rootHasHardcodedStarPermission() {
        PermissionService svc = new PermissionService(null, null);
        assertEquals(Set.of("*"), svc.permissionsOf(RootAccount.ROOT_ID),
                "root 的权限应为硬编码通配符 *");
        assertTrue(svc.hasPermission(RootAccount.ROOT_ID, "lingconsole.app.advanced.appA"));
        assertTrue(svc.hasPermission(RootAccount.ROOT_ID, "lingconsole.node.write.node2"));
        assertTrue(svc.hasPermission(RootAccount.ROOT_ID, "any.arbitrary.key"));
    }

    @Test
    void userManagementAndAssignAreGrantableNodes() {
        assertTrue(Permissions.GRANTABLE.contains(Permissions.USER_MANAGE),
                "user.manage 应为可授权权限节点");
        assertTrue(Permissions.GRANTABLE.contains(Permissions.PERMISSION_ASSIGN),
                "permission.assign 应为可授权权限节点");
        assertEquals(Set.of("*"), Permissions.ROOT_ALL);
    }
}
