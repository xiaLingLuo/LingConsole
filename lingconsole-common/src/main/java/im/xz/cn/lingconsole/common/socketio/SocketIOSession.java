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
package im.xz.cn.lingconsole.common.socketio;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


public class SocketIOSession {

    private final String sid;
    private final Sender sender;
    private final CloseHandler closeHandler;
    private volatile long lastActivity;
    private volatile long lastPongAt;
    private volatile boolean closed;
    private final AtomicBoolean authenticated = new AtomicBoolean();
    private long eventWindowStartedAt;
    private int eventsInWindow;
    private final java.util.Set<String> boundNamespaces = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile Map<String, String> cookies = Map.of();
    private volatile String remoteIp;
    private volatile String origin;

    @FunctionalInterface
    public interface Sender {
        void send(String frame);
    }

    @FunctionalInterface
    public interface CloseHandler {
        void close(int statusCode, String reason);
    }

    public SocketIOSession(String sid, Sender sender, CloseHandler closeHandler) {
        this.sid = sid;
        this.sender = sender;
        this.closeHandler = closeHandler;
        long now = System.currentTimeMillis();
        this.lastActivity = now;
        this.lastPongAt = now;
    }

    public String sid() {
        return sid;
    }

    public void setCookies(java.util.Map<String, String> cookies) {
        this.cookies = cookies == null ? Map.of() : Map.copyOf(cookies);
    }

    public void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }

    public String remoteIp() {
        return remoteIp;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String origin() {
        return origin;
    }

    public String cookie(String name) {
        return name == null ? null : cookies.get(name);
    }

    public boolean isClosed() {
        return closed;
    }

    public void markClosed() {
        this.closed = true;
    }

    public void markPong() {
        long now = System.currentTimeMillis();
        this.lastActivity = now;
        this.lastPongAt = now;
    }

    public void markActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public long lastActivity() {
        return lastActivity;
    }

    public long lastPongAt() {
        return lastPongAt;
    }

    public void send(String frame) {
        if (!closed) {
            sender.send(frame);
        }
    }

    public void bind(String namespace) {
        boundNamespaces.add(namespace);
    }

    public boolean markAuthenticated() {
        return authenticated.compareAndSet(false, true);
    }

    public boolean isAuthenticated() {
        return authenticated.get();
    }

    public synchronized boolean allowEvent(int limit, long windowMillis) {
        if (limit <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (eventWindowStartedAt == 0 || now - eventWindowStartedAt >= windowMillis) {
            eventWindowStartedAt = now;
            eventsInWindow = 0;
        }
        return ++eventsInWindow <= limit;
    }

    public void unbind(String namespace) {
        boundNamespaces.remove(namespace);
    }

    public boolean hasNamespace(String namespace) {
        return boundNamespaces.contains(namespace);
    }

    public java.util.Set<String> boundNamespaces() {
        return boundNamespaces;
    }

    public void close() {
        close(1000, "bye");
    }

    public void close(int statusCode, String reason) {
        if (!closed) {
            closed = true;
            closeHandler.close(statusCode, reason);
        }
    }
}
