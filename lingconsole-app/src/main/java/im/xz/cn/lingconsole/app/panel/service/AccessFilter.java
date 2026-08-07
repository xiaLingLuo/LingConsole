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
import im.xz.cn.lingconsole.app.panel.model.Node;

import java.util.ArrayList;
import java.util.List;


public final class AccessFilter {

    private AccessFilter() {
    }

    public static boolean hasAnyAppScope(AuthUser auth) {
        if (auth == null) {
            return false;
        }
        for (String p : auth.permissions()) {
            if (p == null) {
                continue;
            }
            if (p.equals("*")) {
                return true;
            }
            if (p.startsWith("lingconsole.app.")
                    || p.startsWith("lingconsole.file.app.")
                    || p.startsWith("lingconsole.terminal.app.")) {
                return true;
            }
        }
        return false;
    }

    public static List<Node> visibleNodes(AuthUser auth, List<Node> nodes) {
        if (auth == null) {
            return List.of();
        }
        if (auth.hasPermission("lingconsole.node.read.*")) {
            return nodes;
        }
        List<Node> result = new ArrayList<>();
        for (Node n : nodes) {
            String id = n.getId();
            if (auth.hasPermission("lingconsole.node.read." + id)
                    || auth.hasPermission("lingconsole.node.write." + id)
                    || auth.hasPermission("lingconsole.file.node." + id)
                    || auth.hasPermission("lingconsole.terminal.node." + id)
                    || auth.hasPermission("lingconsole.monitor.read." + id)) {
                result.add(n);
            }
        }
        return result;
    }
}
