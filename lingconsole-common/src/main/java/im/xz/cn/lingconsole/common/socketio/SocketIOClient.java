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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import im.xz.cn.lingconsole.common.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class SocketIOClient {

    private static final Logger log = LoggerFactory.getLogger(SocketIOClient.class);

    private final String host;
    private final int port;
    
    private final String namespace;
    
    private final String cleanNamespace;
    private final boolean useSsl;
    private final Duration connectTimeout;

    private final Map<String, List<SocketIOClientEventHandler>> eventHandlers = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Object>> pendingRequests = new ConcurrentHashMap<>();

    private WebSocket ws;
    private HttpClient client;
    private volatile CountDownLatch readyLatch = new CountDownLatch(1);
    private volatile boolean connected;
    private volatile String sid;
    private volatile boolean closed;
    private volatile String cookieHeader;

    public SocketIOClient(String host, int port, String namespace) {
        this(host, port, namespace, false, Duration.ofSeconds(10));
    }

    public SocketIOClient(String host, int port, String namespace, boolean useSsl, Duration connectTimeout) {
        this.host = host;
        this.port = port;
        this.namespace = namespace;
        
        int q = namespace.indexOf('?');
        this.cleanNamespace = q == -1 ? namespace : namespace.substring(0, q);
        this.useSsl = useSsl;
        this.connectTimeout = connectTimeout;
    }

    
    public SocketIOClient(String url, String namespace) {
        this(url, namespace, Duration.ofSeconds(10));
    }

    public SocketIOClient(String url, String namespace, Duration connectTimeout) {
        this(parseHost(url), parsePort(url), namespace, isSsl(url), connectTimeout);
    }

    private static boolean isSsl(String url) {
        return url != null && url.startsWith("wss://");
    }

    private static String parseHost(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("节点 URL 不能为空");
        }
        String rest = url.substring(url.indexOf("://") + 3);
        int colon = rest.indexOf(':');
        return colon == -1 ? rest : rest.substring(0, colon);
    }

    private static int parsePort(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("节点 URL 不能为空");
        }
        String rest = url.substring(url.indexOf("://") + 3);
        int colon = rest.indexOf(':');
        if (colon != -1) {
            try {
                return Integer.parseInt(rest.substring(colon + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("节点 URL 端口无效: " + url);
            }
        }
        return isSsl(url) ? 443 : 80;
    }

    public boolean isConnected() {
        return connected && !closed && ws != null;
    }

    
    
    

    public SocketIOClient on(String event, SocketIOClientEventHandler handler) {
        eventHandlers.computeIfAbsent(event, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(handler);
        return this;
    }

    
    public SocketIOClient cookie(String cookieHeader) {
        this.cookieHeader = cookieHeader;
        return this;
    }

    
    
    

    public boolean connect() {
        if (connected) {
            return true;
        }
        try {
            String scheme = useSsl ? "wss" : "ws";
            String uri = scheme + "://" + host + ":" + port + "/socket.io/?EIO=3&transport=websocket";
            client = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .build();
            readyLatch = new CountDownLatch(1);
            java.net.http.WebSocket.Builder builder = client.newWebSocketBuilder();
            if (cookieHeader != null && !cookieHeader.isBlank()) {
                builder.header("Cookie", cookieHeader);
            }
            ws = builder
                    .buildAsync(URI.create(uri), new Listener())
                    .get(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
            
            waitForReady();
            return connected;
        } catch (Exception e) {
            log.error("Socket.IO 连接失败: {}:{} {}", host, port, namespace, e);
            return false;
        }
    }

    private void waitForReady() throws InterruptedException {
        if (!readyLatch.await(connectTimeout.toMillis(), TimeUnit.MILLISECONDS) && !connected) {
            log.warn("Socket.IO 连接超时: {}:{}{}", host, port, namespace);
        }
    }

    public void disconnect() {
        closed = true;
        connected = false;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
            }
        }
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    
    
    

    
    public void emit(String event, Object data) {
        sendFrame(encodeEvent(event, data));
    }

    
    public CompletableFuture<Object> request(String event, Object data) {
        String uuid = IdUtil.uuidShort();
        CompletableFuture<Object> future = new CompletableFuture<>();
        pendingRequests.put(uuid, future);
        sendFrame(encodeEvent(event, Map.of("uuid", uuid, "data", data)));
        return future;
    }

    
    public Object requestBlocking(String event, Object data, long timeoutMs) throws Exception {
        try {
            return request(event, data).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new java.util.concurrent.TimeoutException("请求超时: " + event);
        } catch (ExecutionException e) {
            throw new Exception("请求失败: " + event, e.getCause());
        }
    }

    private void sendFrame(String frame) {
        if (!connected || ws == null) {
            throw new IllegalStateException("Socket.IO 未连接");
        }
        ws.sendText(frame, true);
    }

    
    
    

    private String encodeEvent(String event, Object data) {
        ArrayNode arr = ApiResponse.mapper().createArrayNode();
        arr.add(event);
        if (data != null) {
            arr.addPOJO(data);
        }
        String socketPacket = "2" + (cleanNamespace.equals("/") ? "" : cleanNamespace + ",") + arr;
        return "4" + socketPacket;
    }

    private void handleFrame(String frame, WebSocket webSocket) {
        if (frame == null || frame.isEmpty()) {
            return;
        }
        char type = frame.charAt(0);
        String data = frame.length() > 1 ? frame.substring(1) : "";
        switch (type) {
            case '0' -> handleOpen(data, webSocket);
            case '2' -> safeSendText(webSocket, "3");  
            case '3' -> {  }
            case '4' -> handleSocketIOPacket(data);
            default -> log.debug("忽略客户端 Engine.IO 帧: {}", type);
        }
    }

    private void safeSendText(WebSocket webSocket, String text) {
        if (webSocket == null) {
            return;
        }
        try {
            webSocket.sendText(text, true);
        } catch (Exception e) {
            log.debug("Socket.IO 发送失败: {}", text, e);
        }
    }

    private void handleOpen(String data, WebSocket webSocket) {
        try {
            JsonNode json = ApiResponse.mapper().readTree(data);
            sid = json.path("sid").asText();
        } catch (Exception e) {
            log.error("解析 Engine.IO open 失败: {}", data, e);
        }
        
        String connectPacket = "40" + (namespace.equals("/") ? "" : namespace + ",");
        safeSendText(webSocket, connectPacket);
        connected = true;
        readyLatch.countDown();
        log.info("Socket.IO 客户端已连接: {}:{} ns={} sid={}", host, port, namespace, sid);
    }

    private void handleSocketIOPacket(String packet) {
        if (packet == null || packet.isEmpty()) {
            return;
        }
        char type = packet.charAt(0);
        String body = packet.length() > 1 ? packet.substring(1) : "";
        switch (type) {
            case '0' -> connected = true;
            case '1' -> connected = false;
            case '2' -> handleEvent(body);
            default -> log.debug("忽略 Socket.IO 客户端数据包: {}", type);
        }
    }

    private void handleEvent(String body) {
        
        String payload = body;
        if (body.startsWith("/")) {
            int comma = body.indexOf(',');
            if (comma != -1) {
                payload = body.substring(comma + 1);
            }
        }
        try {
            JsonNode arr = ApiResponse.mapper().readTree(payload);
            if (!arr.isArray() || arr.isEmpty()) {
                return;
            }
            String event = arr.get(0).asText();
            JsonNode data = arr.size() > 1 ? arr.get(1) : null;

            
            if (data != null && data.isObject() && data.has("uuid")) {
                String uuid = data.get("uuid").asText();
                CompletableFuture<Object> future = pendingRequests.remove(uuid);
                if (future != null) {
                    future.complete(data);
                    return;
                }
            }

            List<SocketIOClientEventHandler> handlers = eventHandlers.get(event);
            if (handlers != null) {
                handlers.forEach(h -> h.handle(this, event, data));
            }
        } catch (Exception e) {
            log.error("解析 Socket.IO 客户端事件失败: {}", payload, e);
        }
    }

    
    
    

    private class Listener implements WebSocket.Listener {

        private StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            
            ws = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                handleFrame(textBuffer.toString(), webSocket);
                textBuffer = new StringBuilder();
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            closed = true;
            log.info("Socket.IO 客户端连接关闭: {}:{}, status={}, reason={}", host, port, statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected = false;
            closed = true;
            log.error("Socket.IO 客户端错误: {}:{}", host, port, error);
        }
    }
}
