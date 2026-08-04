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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class PermissionMatcherTest {

    @Test
    void exactMatch() {
        assertTrue(PermissionMatcher.matches("lingconsole.user.manage", "lingconsole.user.manage"));
        assertFalse(PermissionMatcher.matches("lingconsole.user.manage", "lingconsole.node.read"));
    }

    @Test
    void globalWildcard() {
        assertTrue(PermissionMatcher.matches("*", "lingconsole.user.manage"));
        assertTrue(PermissionMatcher.matches("*", "exampleaddon.manage"));
    }

    @Test
    void prefixWildcardMatchesAllDepths() {
        assertTrue(PermissionMatcher.matches("lingconsole.*", "lingconsole.user.manage"));
        assertTrue(PermissionMatcher.matches("lingconsole.*", "lingconsole.node.read"));
        assertTrue(PermissionMatcher.matches("lingconsole.*", "lingconsole.user.manage.app"));
        assertFalse(PermissionMatcher.matches("lingconsole.*", "exampleaddon.manage"));
        assertTrue(PermissionMatcher.matches("exampleaddon.*", "exampleaddon.manage"));
    }

    @Test
    void singleSegmentWildcard() {
        assertTrue(PermissionMatcher.matches("lingconsole.user.*", "lingconsole.user.manage"));
        assertFalse(PermissionMatcher.matches("lingconsole.user.*", "lingconsole.node.manage"));
        assertTrue(PermissionMatcher.matches("lingconsole.*.manage", "lingconsole.user.manage"));
        assertFalse(PermissionMatcher.matches("lingconsole.*.read", "lingconsole.user.manage"));
    }

    @Test
    void barePatternDoesNotGrantScopedKey() {
        assertFalse(PermissionMatcher.matches("lingconsole.node.read", "lingconsole.node.read.n1"));
        assertFalse(PermissionMatcher.matches("lingconsole.app.write", "lingconsole.app.write.n1.appX"));
        assertFalse(PermissionMatcher.matches("lingconsole.file.app", "lingconsole.file.app.n1.appX"));
        assertFalse(PermissionMatcher.matches("lingconsole.app.advanced", "lingconsole.app.advanced.n1.appX"));
    }

    @Test
    void scopedPatternDoesNotGrantOtherScope() {
        assertTrue(PermissionMatcher.matches("lingconsole.node.read.n1", "lingconsole.node.read.n1"));
        assertFalse(PermissionMatcher.matches("lingconsole.node.read.n1", "lingconsole.node.read.n2"));
        assertFalse(PermissionMatcher.matches("lingconsole.node.read.n1", "lingconsole.node.write.n1"));
    }

    @Test
    void suffixWildcardGrantsScopedAndDeeper() {
        assertTrue(PermissionMatcher.matches("lingconsole.node.read.*", "lingconsole.node.read.n1"));
        assertTrue(PermissionMatcher.matches("lingconsole.app.read.*", "lingconsole.app.read.n1.appX"));
        assertTrue(PermissionMatcher.matches("lingconsole.app.read.*", "lingconsole.app.read.n1.appX.deeper"));
        assertFalse(PermissionMatcher.matches("lingconsole.app.read.*", "lingconsole.app.write.n1"));
    }
}
