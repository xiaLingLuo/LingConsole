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
package im.xz.cn.lingconsole.common.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserRoleTest {

    @Test
    void roleValues() {
        assertEquals(0, UserRole.ROOT.value());
        assertEquals(1, UserRole.NORMAL.value());
    }

    @Test
    void fromValue() {
        assertEquals(UserRole.ROOT, UserRole.fromValue(0));
        assertEquals(UserRole.NORMAL, UserRole.fromValue(1));
        assertEquals(UserRole.NORMAL, UserRole.fromValue(99));
        assertEquals(UserRole.NORMAL, UserRole.fromValue(2));
        assertEquals(UserRole.NORMAL, UserRole.fromValue(-1));
    }
}
