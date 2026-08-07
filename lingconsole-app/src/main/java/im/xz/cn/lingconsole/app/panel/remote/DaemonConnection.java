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
package im.xz.cn.lingconsole.app.panel.remote;

import im.xz.cn.lingconsole.app.panel.model.Node;
import im.xz.cn.lingconsole.common.socketio.SocketIOClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class DaemonConnection {

    private static final Logger log = LoggerFactory.getLogger(DaemonConnection.class);

    private final Node node;
    private SocketIOClient client;

    public DaemonConnection(Node node) {
        this.node = node;
    }

    public Node node() {
        return node;
    }

    
    public synchronized boolean connect() {
        disconnect();
        client = new SocketIOClient(node.getUrl(), "/daemon", java.time.Duration.ofSeconds(4));
        if (!client.connect()) {
            client.disconnect();
            client = null;
            return false;
        }
        try {
            CompletableFuture<Object> future = client.request("auth", java.util.Map.of("key", node.getKey()));
            Object resp = future.get(4, TimeUnit.SECONDS);
            if (resp instanceof com.fasterxml.jackson.databind.JsonNode n
                    && n.path("status").asInt(-1) == 200) {
                log.info("节点连接成功: {} ({})", node.getName(), node.getUrl());
                return true;
            }
        } catch (Exception e) {
            log.warn("节点认证失败: {}", node.getName(), e);
        }
        disconnect();
        return false;
    }

    public synchronized void disconnect() {
        if (client != null) {
            client.disconnect();
            client = null;
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public SocketIOClient client() {
        return client;
    }

    public Object requestBlocking(String event, Object data, long timeoutMs) throws Exception {
        if (client == null || !client.isConnected()) {
            throw new IllegalStateException("节点未连接: " + node.getName());
        }
        return client.requestBlocking(event, data, timeoutMs);
    }

    public CompletableFuture<Object> request(String event, Object data) {
        if (client == null || !client.isConnected()) {
            CompletableFuture<Object> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("节点未连接: " + node.getName()));
            return failed;
        }
        return client.request(event, data);
    }
}
