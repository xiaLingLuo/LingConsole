/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package im.xz.cn.lingconsole.common.socketio;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SocketIOServerProtocolTest {

    @Test
    void rejectedNamespaceIsNeverBoundOrAcknowledged() {
        SocketIOServer server = new SocketIOServer();
        Fixture fixture = new Fixture();
        server.onConnect("/panel", (connection, query) -> SocketIOConnectResult.reject("bad token"));

        server.handleEngineIOPacket(fixture.session, "40/panel,token=bad");

        assertFalse(fixture.session.hasNamespace("/panel"));
        assertFalse(fixture.frames.contains("40/panel,"));
        assertTrue(fixture.frames.stream().anyMatch(frame ->
                frame.startsWith("44/panel,") && frame.contains("bad token")));
        server.stop();
    }

    @Test
    void eventsAreDispatchedOnlyAfterSuccessfulBindingAndNullDataIsAllowed() {
        SocketIOServer server = new SocketIOServer();
        Fixture fixture = new Fixture();
        AtomicInteger calls = new AtomicInteger();
        server.onConnect("/panel", (connection, query) -> {
            connection.markAuthenticated();
            return SocketIOConnectResult.accept();
        });
        server.on("/panel", "test", (connection, event, data) -> {
            assertNull(data);
            calls.incrementAndGet();
        });

        server.handleEngineIOPacket(fixture.session, "42/panel,[\"test\"]");
        assertEquals(0, calls.get());

        server.handleEngineIOPacket(fixture.session, "40/panel,");
        assertTrue(fixture.session.hasNamespace("/panel"));
        assertTrue(fixture.frames.contains("40/panel,"));
        server.handleEngineIOPacket(fixture.session, "42/panel,[\"test\"]");
        assertEquals(1, calls.get());
        server.stop();
    }

    @Test
    void closesSessionsThatExceedEventRate() {
        SocketIOServer server = new SocketIOServer().setEventRateLimit(1, Duration.ofSeconds(1));
        Fixture fixture = new Fixture();
        server.onConnect("/panel", (connection, query) -> {
            connection.markAuthenticated();
            return SocketIOConnectResult.accept();
        });
        server.registerNamespace("/panel").on("/panel", "test", (connection, event, data) -> { });
        server.handleEngineIOPacket(fixture.session, "40/panel,");

        server.handleEngineIOPacket(fixture.session, "42/panel,[\"test\"]");
        server.handleEngineIOPacket(fixture.session, "42/panel,[\"test\"]");

        assertEquals(1008, fixture.closeCode.get());
        server.stop();
    }

    @Test
    void enforcesTextBinaryAndAggregatedByteLimits() {
        SocketIOServer textServer = new SocketIOServer().setMaxTextMessageBytes(3);
        Fixture text = new Fixture();
        textServer.acceptTextMessage(text.session, "四四");
        assertEquals(1009, text.closeCode.get());
        textServer.stop();

        SocketIOServer binaryServer = new SocketIOServer().setMaxBinaryMessageBytes(2);
        Fixture binary = new Fixture();
        binaryServer.acceptBinaryMessage(binary.session, 3);
        assertEquals(1009, binary.closeCode.get());
        binaryServer.stop();

        SocketIOServer aggregateServer = new SocketIOServer()
                .setMaxTextMessageBytes(100)
                .setMaxAggregatedMessageBytes(4);
        Fixture aggregate = new Fixture();
        aggregateServer.acceptTextMessage(aggregate.session, "12345");
        assertEquals(1009, aggregate.closeCode.get());
        aggregateServer.stop();
    }

    @Test
    void enforcesGlobalAndPerIpUnauthenticatedConnectionLimits() {
        SocketIOServer globalServer = new SocketIOServer().setMaxConnections(1);
        Fixture globalFirst = new Fixture("first", "127.0.0.1");
        Fixture globalSecond = new Fixture("second", "127.0.0.2");
        assertTrue(globalServer.admitSession(globalFirst.session));
        assertFalse(globalServer.admitSession(globalSecond.session));
        assertEquals(1013, globalSecond.closeCode.get());
        globalServer.stop();

        SocketIOServer perIpServer = new SocketIOServer().setMaxUnauthenticatedConnectionsPerIp(1);
        Fixture ipFirst = new Fixture("first", "127.0.0.1");
        Fixture ipSecond = new Fixture("second", "127.0.0.1");
        assertTrue(perIpServer.admitSession(ipFirst.session));
        assertFalse(perIpServer.admitSession(ipSecond.session));
        assertEquals(1008, ipSecond.closeCode.get());
        perIpServer.stop();
    }

    @Test
    void closesSessionsThatDoNotBindBeforeAuthenticationTimeout() throws InterruptedException {
        SocketIOServer server = new SocketIOServer().setAuthenticationTimeout(Duration.ofMillis(20));
        Fixture fixture = new Fixture();
        assertTrue(server.admitSession(fixture.session));
        server.scheduleAuthenticationTimeout(fixture.session);

        assertTrue(fixture.closed.await(2, TimeUnit.SECONDS));
        assertEquals(1008, fixture.closeCode.get());
        server.stop();
    }

    @Test
    void namespaceBindingDoesNotAuthenticateAndEventsFailClosedUntilExplicitAuthentication() {
        SocketIOServer server = new SocketIOServer();
        Fixture fixture = new Fixture("first", "127.0.0.1");
        AtomicInteger protectedCalls = new AtomicInteger();
        AtomicInteger authCalls = new AtomicInteger();
        server.registerNamespace("/daemon")
                .allowUnauthenticatedEvent("/daemon", "auth")
                .on("/daemon", "auth", (connection, event, data) -> {
                    authCalls.incrementAndGet();
                    assertTrue(connection.markAuthenticated());
                    assertFalse(connection.markAuthenticated());
                })
                .on("/daemon", "protected", (connection, event, data) -> protectedCalls.incrementAndGet());
        assertTrue(server.admitSession(fixture.session));

        server.handleEngineIOPacket(fixture.session, "40/daemon,");
        assertTrue(fixture.session.hasNamespace("/daemon"));
        assertFalse(fixture.session.isAuthenticated());
        server.handleEngineIOPacket(fixture.session, "42/daemon,[\"protected\"]");
        assertEquals(0, protectedCalls.get());

        server.handleEngineIOPacket(fixture.session, "42/daemon,[\"auth\"]");
        assertEquals(1, authCalls.get());
        assertTrue(fixture.session.isAuthenticated());
        server.handleEngineIOPacket(fixture.session, "42/daemon,[\"protected\"]");
        assertEquals(1, protectedCalls.get());
        server.stop();
    }

    @Test
    void ownerRegistrationCanBeReplacedAndRemovedButCannotOverrideCoreEvents() {
        SocketIOServer server = new SocketIOServer();
        Fixture fixture = new Fixture();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        server.onConnect("/panel", (connection, query) -> {
            connection.markAuthenticated();
            return SocketIOConnectResult.accept();
        });
        server.on("addon", "/panel", "addon:event", (connection, event, data) -> first.incrementAndGet());
        server.on("addon", "/panel", "addon:event", (connection, event, data) -> second.incrementAndGet());
        server.on("/panel", "core:event", (connection, event, data) -> { });

        assertThrows(IllegalStateException.class,
                () -> server.on("addon", "/panel", "core:event", (connection, event, data) -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> server.on(SocketIOServer.CORE_EVENT_OWNER, "/panel", "fake", (connection, event, data) -> { }));

        server.handleEngineIOPacket(fixture.session, "40/panel,");
        server.handleEngineIOPacket(fixture.session, "42/panel,[\"addon:event\"]");
        assertEquals(0, first.get());
        assertEquals(1, second.get());
        assertEquals(1, server.unregisterOwner("addon"));
        assertEquals(0, server.unregisterOwner("addon"));
        server.handleEngineIOPacket(fixture.session, "42/panel,[\"addon:event\"]");
        assertEquals(1, second.get());
        assertThrows(IllegalArgumentException.class,
                () -> server.unregisterOwner(SocketIOServer.CORE_EVENT_OWNER));
        server.stop();
    }

    private static final class Fixture {
        private final List<String> frames = new CopyOnWriteArrayList<>();
        private final AtomicInteger closeCode = new AtomicInteger(-1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private final SocketIOSession session;

        private Fixture() {
            this("sid", null);
        }

        private Fixture(String sid, String remoteIp) {
            session = new SocketIOSession(sid, frames::add, (code, reason) -> {
                closeCode.set(code);
                closed.countDown();
            });
            session.setRemoteIp(remoteIp);
        }
    }
}
