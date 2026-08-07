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

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SocketIOClientResourceTest {

    @Test
    void fragmentedTextAndBinaryBuffersAreBounded() {
        SocketIOClient client = new SocketIOClient("localhost", 1, "/panel")
                .setMaxFragmentBufferBytes(5);
        AtomicInteger closeCode = new AtomicInteger(-1);
        WebSocket socket = fakeSocket(closeCode);

        SocketIOClient.Listener textListener = client.new Listener();
        textListener.onText(socket, "abc", false);
        textListener.onText(socket, "def", true);
        assertEquals(1009, closeCode.get());

        closeCode.set(-1);
        SocketIOClient.Listener binaryListener = client.new Listener();
        binaryListener.onBinary(socket, ByteBuffer.wrap(new byte[3]), false);
        binaryListener.onBinary(socket, ByteBuffer.wrap(new byte[3]), true);
        assertEquals(1009, closeCode.get());

        closeCode.set(-1);
        SocketIOClient unicodeClient = new SocketIOClient("localhost", 1, "/panel")
                .setMaxFragmentBufferBytes(3);
        SocketIOClient.Listener unicodeListener = unicodeClient.new Listener();
        unicodeListener.onText(socket, "\ud83d", false);
        unicodeListener.onText(socket, "\ude00", true);
        assertEquals(1009, closeCode.get());
    }

    @Test
    void requestAllowsNullAndTimeoutRemovesPendingFuture() throws Exception {
        SocketIOClient client = connectedClient();

        CompletableFuture<Object> future = client.request("test", null, Duration.ofMillis(20));
        CompletionException error = assertThrows(CompletionException.class, future::join);

        assertInstanceOf(java.util.concurrent.TimeoutException.class, error.getCause());
        assertEquals(0, pendingCount(client));
        client.disconnect();
    }

    @Test
    void sendFailureAndSocketErrorFailAndRemovePendingFutures() throws Exception {
        SocketIOClient sendFailureClient = connectedClient();
        setField(sendFailureClient, "ws", fakeSocket(new AtomicInteger(), true));
        CompletableFuture<Object> sendFailure = sendFailureClient.request("test", null, Duration.ofSeconds(1));
        assertThrows(CompletionException.class, sendFailure::join);
        assertEquals(0, pendingCount(sendFailureClient));

        SocketIOClient errorClient = connectedClient();
        CompletableFuture<Object> pending = errorClient.request("test", null, Duration.ofSeconds(1));
        SocketIOClient.Listener listener = errorClient.new Listener();
        setField(errorClient, "activeListener", listener);
        listener.onError((WebSocket) field(errorClient, "ws"), new IllegalStateException("broken"));
        assertThrows(CompletionException.class, pending::join);
        assertEquals(0, pendingCount(errorClient));
        sendFailureClient.disconnect();
        errorClient.disconnect();
    }

    @Test
    void disconnectClosesAndClearsResourcesOnlyOnce() throws Exception {
        SocketIOClient client = connectedClient();
        AtomicInteger closeCalls = new AtomicInteger();
        HttpClient httpClient = HttpClient.newHttpClient();
        setField(client, "ws", fakeSocket(new AtomicInteger(), false, closeCalls));
        setField(client, "client", httpClient);

        client.disconnect();
        client.disconnect();

        assertEquals(1, closeCalls.get());
        assertNull(field(client, "ws"));
        assertNull(field(client, "client"));
        assertFalse(client.isConnected());
        assertTrue(httpClient.isTerminated());
    }

    private static SocketIOClient connectedClient() throws Exception {
        SocketIOClient client = new SocketIOClient("localhost", 1, "/panel");
        setField(client, "ws", fakeSocket(new AtomicInteger()));
        setField(client, "connected", true);
        ((AtomicBoolean) field(client, "resourcesClosed")).set(false);
        return client;
    }

    private static WebSocket fakeSocket(AtomicInteger closeCode) {
        return fakeSocket(closeCode, false);
    }

    private static WebSocket fakeSocket(AtomicInteger closeCode, boolean failSend) {
        return fakeSocket(closeCode, failSend, null);
    }

    private static WebSocket fakeSocket(AtomicInteger closeCode, boolean failSend, AtomicInteger closeCalls) {
        AtomicReference<WebSocket> reference = new AtomicReference<>();
        WebSocket socket = (WebSocket) Proxy.newProxyInstance(WebSocket.class.getClassLoader(),
                new Class<?>[]{WebSocket.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "sendText", "sendBinary", "sendPing", "sendPong" -> failSend
                                ? CompletableFuture.failedFuture(new IllegalStateException("send failed"))
                                : CompletableFuture.completedFuture(reference.get());
                        case "sendClose" -> {
                            closeCode.set((Integer) args[0]);
                            if (closeCalls != null) {
                                closeCalls.incrementAndGet();
                            }
                            yield CompletableFuture.completedFuture(reference.get());
                        }
                        case "getSubprotocol" -> "";
                        case "isOutputClosed", "isInputClosed" -> false;
                        case "request", "abort" -> null;
                        case "toString" -> "FakeWebSocket";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
        reference.set(socket);
        return socket;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = SocketIOClient.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int pendingCount(SocketIOClient client) throws Exception {
        return ((java.util.Map<?, ?>) field(client, "pendingRequests")).size();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = SocketIOClient.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
