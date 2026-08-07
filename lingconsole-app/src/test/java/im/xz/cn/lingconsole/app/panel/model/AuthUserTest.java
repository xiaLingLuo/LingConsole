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
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthUserTest {

    private AuthUser auth(String... perms) {
        User user = new User();
        user.setId("u1");
        user.setUsername("alice");
        user.setRole(UserRole.NORMAL);
        return new AuthUser(user, Set.of(perms));
    }

    @Test
    void exactPermissionMatches() {
        AuthUser a = auth("lingconsole.node.read.node1");
        assertTrue(a.hasPermission("lingconsole.node.read.node1"));
        assertFalse(a.hasPermission("lingconsole.node.read.node2"));
    }

    @Test
    void writeImpliesReadAtSameScope() {
        AuthUser a = auth("lingconsole.app.write.appA");
        assertTrue(a.hasPermission("lingconsole.app.read.appA"),
                "app.write.<scope> 应隐含同作用域 app.read");
        assertFalse(a.hasPermission("lingconsole.app.read.appB"),
                "不得跨应用生效");
    }

    @Test
    void advancedImpliesReadAtSameScope() {
        AuthUser a = auth("lingconsole.app.advanced.appA");
        assertTrue(a.hasPermission("lingconsole.app.read.appA"));
        assertFalse(a.hasPermission("lingconsole.app.read.appB"));
    }

    @Test
    void wildcardWriteImpliesWildcardRead() {
        AuthUser a = auth("lingconsole.app.write.*");
        assertTrue(a.hasPermission("lingconsole.app.read.appA"));
    }

    @Test
    void nodeWriteDoesNotImplyNodeRead() {
        AuthUser a = auth("lingconsole.node.write.node1");
        assertFalse(a.hasPermission("lingconsole.node.read.node1"),
                "节点权限不设隐式继承");
    }

    @Test
    void nullKeyDenied() {
        AuthUser a = auth("lingconsole.node.read.node1");
        assertFalse(a.hasPermission(null));
    }

    @Test
    void starPermissionMatchesEverything() {
        AuthUser a = auth("*");
        assertTrue(a.hasPermission("lingconsole.app.advanced.node1.appA"));
        assertTrue(a.hasPermission("lingconsole.node.write.node2"));
        assertTrue(a.hasPermission("anything.else"));
    }
}
