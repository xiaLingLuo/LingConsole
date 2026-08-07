/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package im.xz.cn.lingconsole.common.addon;

import im.xz.cn.lingconsole.common.socketio.SocketIOServer;
import im.xz.cn.lingconsole.common.socketio.SocketIOSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AddonSocketSupportTest {

    @Test
    void registrationRequiresPermission() {
        AddonSocketRegistry registry = new AddonSocketRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("addon", "/panel", "event", null, (connection, event, data) -> { }));
    }

    @Test
    void authenticationRunsBeforePermissionAndHandler() throws Exception {
        AddonSocketRegistry registry = new AddonSocketRegistry();
        AtomicInteger handled = new AtomicInteger();
        registry.register("addon", "/panel", "event", "addon.manage",
                (connection, event, data) -> handled.incrementAndGet());
        SocketIOServer server = new SocketIOServer();
        AtomicBoolean authenticated = new AtomicBoolean();
        AtomicBoolean permitted = new AtomicBoolean();
        AtomicInteger permissionChecks = new AtomicInteger();
        AddonSocketSupport.apply(server, registry, connection -> authenticated.get(), (connection, permission) -> {
            assertEquals("addon.manage", permission);
            permissionChecks.incrementAndGet();
            return permitted.get();
        });
        server.onConnect("/panel", (connection, query) -> {
            connection.markAuthenticated();
            return im.xz.cn.lingconsole.common.socketio.SocketIOConnectResult.accept();
        });
        List<String> frames = new CopyOnWriteArrayList<>();
        SocketIOSession session = new SocketIOSession("sid", frames::add, (code, reason) -> { });
        send(server, session, "40/panel,");

        send(server, session, "42/panel,[\"event\",{}]");
        assertEquals(0, permissionChecks.get());
        assertEquals(0, handled.get());
        assertTrue(frames.stream().anyMatch(frame -> frame.contains("401")));

        authenticated.set(true);
        send(server, session, "42/panel,[\"event\",{}]");
        assertEquals(1, permissionChecks.get());
        assertEquals(0, handled.get());
        assertTrue(frames.stream().anyMatch(frame -> frame.contains("403")));

        permitted.set(true);
        send(server, session, "42/panel,[\"event\",{}]");
        assertEquals(2, permissionChecks.get());
        assertEquals(1, handled.get());
        assertEquals(1, server.unregisterOwner("addon"));
        send(server, session, "42/panel,[\"event\",{}]");
        assertEquals(1, handled.get());
        server.stop();
    }

    private static void send(SocketIOServer server, SocketIOSession session, String frame) throws Exception {
        java.lang.reflect.Method method = SocketIOServer.class
                .getDeclaredMethod("handleEngineIOPacket", SocketIOSession.class, String.class);
        method.setAccessible(true);
        method.invoke(server, session, frame);
    }
}
