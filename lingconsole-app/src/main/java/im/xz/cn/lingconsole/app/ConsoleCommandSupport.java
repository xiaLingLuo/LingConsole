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
package im.xz.cn.lingconsole.app;

import im.xz.cn.lingconsole.addon.AddonState;
import im.xz.cn.lingconsole.addon.CommandSender;
import im.xz.cn.lingconsole.common.addon.AddonManager;
import im.xz.cn.lingconsole.common.command.ConsoleCommandDispatcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


public final class ConsoleCommandSupport {

    private ConsoleCommandSupport() {
    }
    public static void startLoop(ConsoleCommandDispatcher dispatcher) {
        Thread t = new Thread(() -> {
            CommandSender sender = message -> {
                String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
                System.out.println("[" + ts + " INFO]: " + message);
            };
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    dispatcher.dispatch(line, sender);
                }
            } catch (IOException _) {

            }
        }, "console-commands");
        t.setDaemon(true);
        t.start();
    }

    public static void registerBuiltins(ConsoleCommandDispatcher dispatcher, AddonManager addonManager) {
        dispatcher.register(ConsoleCommandDispatcher.DEFAULT_NAMESPACE, "addons",
                (command, args, sender) -> listAddons(addonManager, sender));
        dispatcher.register(ConsoleCommandDispatcher.DEFAULT_NAMESPACE, "end",
                (command, args, sender) -> shutdownProgram(sender));
        dispatcher.register(ConsoleCommandDispatcher.DEFAULT_NAMESPACE, "stop",
                (command, args, sender) -> shutdownProgram(sender));
    }

    private static void shutdownProgram(CommandSender sender) {
        sender.sendMessage("正在关闭 LingConsole ...");
        System.exit(0);
    }

    private static void listAddons(AddonManager addonManager, CommandSender sender) {
        List<AddonManager.LoadedAddon> addons =
                addonManager == null ? List.of() : addonManager.addons();
        sender.sendMessage("Console Addons (" + addons.size() + "):");
        StringBuilder sb = new StringBuilder(" - ");
        for (int i = 0; i < addons.size(); i++) {
            AddonManager.LoadedAddon la = addons.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            boolean err = (la.state() != AddonState.LOADED && la.state() != AddonState.ENABLED)
                    || (addonManager != null && addonManager.namespaceConflict(la.descriptor().name()));
            sb.append(la.descriptor().name()).append(err ? "[ERR]" : "[OK]");
        }
        sender.sendMessage(sb.toString());
    }
}
