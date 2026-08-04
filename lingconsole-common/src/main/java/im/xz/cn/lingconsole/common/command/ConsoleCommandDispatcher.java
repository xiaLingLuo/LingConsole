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
package im.xz.cn.lingconsole.common.command;

import im.xz.cn.lingconsole.addon.CommandHandler;
import im.xz.cn.lingconsole.addon.CommandSender;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class ConsoleCommandDispatcher {

    /** 原生指令默认命名空间: lingconsole: */
    public static final String DEFAULT_NAMESPACE = "lingconsole";

    private final Map<String, Map<String, CommandHandler>> namespaces = new ConcurrentHashMap<>();


    public boolean register(String namespace, String command, CommandHandler handler) {
        if (namespace == null || namespace.isBlank() || command == null || command.isBlank() || handler == null) {
            return false;
        }
        Map<String, CommandHandler> cmds =
                namespaces.computeIfAbsent(namespace.trim().toLowerCase(), k -> new ConcurrentHashMap<>());
        return cmds.putIfAbsent(command.trim().toLowerCase(), handler) == null;
    }


    public void unregisterNamespace(String namespace) {
        if (namespace != null) {
            namespaces.remove(namespace.trim().toLowerCase());
        }
    }


    public boolean hasNamespace(String namespace) {
        return namespace != null && namespaces.containsKey(namespace.trim().toLowerCase());
    }

    public int namespaceCount() {
        return namespaces.size();
    }

    public int commandCount() {
        int n = 0;
        for (Map<String, CommandHandler> cmds : namespaces.values()) {
            n += cmds.size();
        }
        return n;
    }


    public void dispatch(String line, CommandSender sender) {
        if (line == null) {
            return;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("\uFEFF")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.isEmpty()) {
            return;
        }
        String[] tokens = trimmed.split("\\s+");
        String first = tokens[0];
        String[] args = Arrays.copyOfRange(tokens, 1, tokens.length);

        String namespace;
        String command;
        int colon = first.indexOf(':');
        if (colon >= 0) {
            namespace = first.substring(0, colon);
            command = first.substring(colon + 1);
        } else {
            namespace = DEFAULT_NAMESPACE;
            command = first;
        }
        if (command.isEmpty()) {
            if (sender != null) {
                sender.sendMessage("未知命令: " + first);
            }
            return;
        }
        Map<String, CommandHandler> cmds = namespaces.get(namespace.toLowerCase());
        CommandHandler handler = cmds == null ? null : cmds.get(command.toLowerCase());
        if (handler == null) {
            if (sender != null) {
                sender.sendMessage("未知命令: " + first);
            }
            return;
        }
        handler.execute(command, args, sender);
    }
}
