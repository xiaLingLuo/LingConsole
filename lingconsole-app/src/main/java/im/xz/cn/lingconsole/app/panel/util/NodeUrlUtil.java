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
package im.xz.cn.lingconsole.app.panel.util;


public final class NodeUrlUtil {

    private NodeUrlUtil() {
    }

    
    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("节点 URL 不能为空");
        }
        String u = url.trim();
        if (!u.startsWith("ws://") && !u.startsWith("wss://")) {
            throw new IllegalArgumentException("节点 URL 必须以 ws:// 或 wss:// 开头");
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        String rest = u.substring(u.indexOf("://") + 3);
        if (rest.isBlank() || rest.startsWith(":")) {
            throw new IllegalArgumentException("节点 URL 缺少主机地址: " + u);
        }
        return u;
    }
}
