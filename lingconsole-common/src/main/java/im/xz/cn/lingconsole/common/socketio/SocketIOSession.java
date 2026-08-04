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


public class SocketIOSession {

    private final String sid;
    private final Sender sender;
    private final CloseHandler closeHandler;

    private volatile long lastActivity;
    private volatile boolean closed;

    
    private final java.util.Set<String> boundNamespaces = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private volatile Map<String, String> cookies = Map.of();

    
    @FunctionalInterface
    public interface Sender {
        void send(String frame);
    }

    
    @FunctionalInterface
    public interface CloseHandler {
        void close();
    }

    public SocketIOSession(String sid, Sender sender, CloseHandler closeHandler) {
        this.sid = sid;
        this.sender = sender;
        this.closeHandler = closeHandler;
        this.lastActivity = System.currentTimeMillis();
    }

    public String sid() {
        return sid;
    }

    public void setCookies(java.util.Map<String, String> cookies) {
        this.cookies = cookies == null ? Map.of() : Map.copyOf(cookies);
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
        this.lastActivity = System.currentTimeMillis();
    }

    public void markActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public long lastActivity() {
        return lastActivity;
    }

    public void send(String frame) {
        if (!closed) {
            sender.send(frame);
        }
    }

    public void bind(String namespace) {
        boundNamespaces.add(namespace);
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
        if (!closed) {
            closed = true;
            closeHandler.close();
        }
    }
}
