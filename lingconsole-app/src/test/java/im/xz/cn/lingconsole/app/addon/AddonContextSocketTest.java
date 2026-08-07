/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package im.xz.cn.lingconsole.app.addon;

import im.xz.cn.lingconsole.addon.AddonContext;
import im.xz.cn.lingconsole.addon.AddonInfo;
import im.xz.cn.lingconsole.common.addon.AddonMenuRegistry;
import im.xz.cn.lingconsole.common.addon.AddonProxyRegistry;
import im.xz.cn.lingconsole.common.addon.AddonSocketRegistry;
import im.xz.cn.lingconsole.common.addon.Slf4jAddonLogger;
import im.xz.cn.lingconsole.common.command.ConsoleCommandDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddonContextSocketTest {

    @TempDir
    Path dataDir;

    @Test
    void normalizesRequiredSocketPermissions() {
        AddonSocketRegistry registry = new AddonSocketRegistry();
        AddonInfo info = new AddonInfo("socketaddon", "1.0", "test", null, null, null, List.of());
        AddonContextImpl context = new AddonContextImpl(info, new Slf4jAddonLogger("socketaddon"),
                null, null, null, null, null, dataDir, registry, new AddonMenuRegistry(),
                new AddonProxyRegistry(), null, new ConsoleCommandDispatcher(), null);

        context.registerSocketEvent("/panel", "relative", "Manage", (connection, event, data) -> { });
        context.registerSocketEvent("/panel", "qualified", "socketaddon.manage", (connection, event, data) -> { });
        context.registerSocketEvent("/panel", "public", AddonContext.PUBLIC, (connection, event, data) -> { });
        context.registerSocketEvent("/panel", "core", "lingconsole.node.read.*", (connection, event, data) -> { });

        assertEquals(List.of("socketaddon.manage", "socketaddon.manage", AddonContext.PUBLIC,
                        "lingconsole.node.read.*"),
                registry.all("socketaddon").stream()
                        .map(AddonSocketRegistry.Registration::requiredPermission)
                        .toList());
    }

    @Test
    void adaptersHeldByDisabledAddonRejectFurtherUse() {
        AddonInfo info = new AddonInfo("closedaddon", "1.0", "test", null, null, null, List.of());
        AddonContextImpl context = new AddonContextImpl(info, new Slf4jAddonLogger("closedaddon"),
                null, null, null, null, null, dataDir, new AddonSocketRegistry(), new AddonMenuRegistry(),
                new AddonProxyRegistry(), null, new ConsoleCommandDispatcher(), null);
        var nodes = context.nodes();
        var apps = context.apps();
        var files = context.files();
        var monitor = context.monitor();
        var exec = context.exec();
        var data = context.data();
        var users = context.users();
        var logs = context.logs();
        var config = context.config();

        context.close();

        assertThrows(IllegalStateException.class, nodes::listNodes);
        assertThrows(IllegalStateException.class, () -> apps.listApps("node"));
        assertThrows(IllegalStateException.class, () -> files.listFiles("node", "/"));
        assertThrows(IllegalStateException.class, () -> monitor.snapshot("node"));
        assertThrows(IllegalStateException.class, () -> exec.exec("node", "true", 1000));
        assertThrows(IllegalStateException.class, data::all);
        assertThrows(IllegalStateException.class, users::listUsers);
        assertThrows(IllegalStateException.class, () -> logs.record("action", "target", "detail"));
        assertThrows(IllegalStateException.class, config::values);
        assertThrows(IllegalStateException.class, context::info);
    }
}
