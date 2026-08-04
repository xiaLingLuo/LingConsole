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

import im.xz.cn.lingconsole.addon.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ConsoleCommandDispatcherTest {

    private static final class Collector implements CommandSender {
        final List<String> messages = new ArrayList<>();

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }
    }

    private static String[] argsOf(String line) {
        return line.trim().split("\\s+", 2).length == 2
                ? line.trim().split("\\s+", 2)[1].split("\\s+")
                : new String[0];
    }

    @Test
    void defaultNamespaceAndExplicitPrefix() {
        ConsoleCommandDispatcher d = new ConsoleCommandDispatcher();
        Collector c = new Collector();
        d.register("lingconsole", "addons", (cmd, args, s) -> s.sendMessage("addons-" + cmd + "-" + args.length));
        d.register("myplugin", "status", (cmd, args, s) -> s.sendMessage("status-" + cmd));

        d.dispatch("addons", c);
        assertEquals(List.of("addons-addons-0"), c.messages);

        c.messages.clear();
        d.dispatch("lingconsole:addons x y", c);
        assertEquals(List.of("addons-addons-2"), c.messages);

        c.messages.clear();
        d.dispatch("myplugin:status", c);
        assertEquals(List.of("status-status"), c.messages);

        c.messages.clear();
        d.dispatch("status", c);
        assertEquals(1, c.messages.size());
        assertTrue(c.messages.get(0).startsWith("未知命令"));
    }

    @Test
    void unknownCommandAndUnknownNamespace() {
        ConsoleCommandDispatcher d = new ConsoleCommandDispatcher();
        Collector c = new Collector();
        d.register("lingconsole", "addons", (cmd, args, s) -> s.sendMessage("ok"));

        d.dispatch("nope", c);
        assertEquals(1, c.messages.size());
        assertTrue(c.messages.get(0).contains("nope"));

        c.messages.clear();
        d.dispatch("ghost:thing", c);
        assertEquals(1, c.messages.size());
        assertTrue(c.messages.get(0).contains("ghost:thing"));
    }

    @Test
    void duplicateCommandRegistrationRejected() {
        ConsoleCommandDispatcher d = new ConsoleCommandDispatcher();
        assertTrue(d.register("lingconsole", "addons", (cmd, args, s) -> { }));
        assertFalse(d.register("lingconsole", "addons", (cmd, args, s) -> { }));
    }

    @Test
    void unregisterNamespaceClearsCommands() {
        ConsoleCommandDispatcher d = new ConsoleCommandDispatcher();
        Collector c = new Collector();
        d.register("myplugin", "hello", (cmd, args, s) -> s.sendMessage("hi"));
        d.dispatch("myplugin:hello", c);
        assertEquals(List.of("hi"), c.messages);

        d.unregisterNamespace("myplugin");
        c.messages.clear();
        d.dispatch("myplugin:hello", c);
        assertTrue(c.messages.get(0).startsWith("未知命令"));
        assertFalse(d.hasNamespace("myplugin"));
    }

    @Test
    void emptyAndBlankLinesIgnored() {
        ConsoleCommandDispatcher d = new ConsoleCommandDispatcher();
        Collector c = new Collector();
        d.dispatch("", c);
        d.dispatch("   ", c);
        d.dispatch(null, c);
        assertTrue(c.messages.isEmpty());
    }

    @Test
    void argsSplitCorrectly() {
        ConsoleCommandDispatcher d = new ConsoleCommandDispatcher();
        Collector c = new Collector();
        d.register("lingconsole", "echo", (cmd, args, s) -> s.sendMessage(String.join("|", args)));
        d.dispatch("echo a bb ccc", c);
        assertEquals(List.of("a|bb|ccc"), c.messages);
    }
}
