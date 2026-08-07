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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;


public class SocketIOClient {

    private static final Logger log = LoggerFactory.getLogger(SocketIOClient.class);
    private static final ScheduledExecutorService REQUEST_TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "socketio-client-timeouts");
                thread.setDaemon(true);
                return thread;
            });

    private final String host;
    private final int port;
    
    private final String namespace;
    
    private final String cleanNamespace;
    private final boolean useSsl;
    private final Duration connectTimeout;

    private final Map<String, List<SocketIOClientEventHandler>> eventHandlers = new ConcurrentHashMap<>();
    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    private volatile WebSocket ws;
    private volatile HttpClient client;
    private volatile Listener activeListener;
    private volatile CountDownLatch readyLatch = new CountDownLatch(1);
    private volatile boolean connected;
    private volatile String sid;
    private volatile boolean closed;
    private volatile String cookieHeader;
    private volatile int maxFragmentBufferBytes = 4 * 1024 * 1024;
    private final AtomicBoolean resourcesClosed = new AtomicBoolean(true);
    private final Object sendLock = new Object();
    private CompletableFuture<Void> sendTail = CompletableFuture.completedFuture(null);

    private static final class PendingRequest {
        private final CompletableFuture<Object> future;
        private volatile ScheduledFuture<?> timeout;

        private PendingRequest(CompletableFuture<Object> future) {
            this.future = future;
        }

        CompletableFuture<Object> future() {
            return future;
        }

        void timeout(ScheduledFuture<?> timeout) {
            this.timeout = timeout;
        }

        void cancelTimeout() {
            ScheduledFuture<?> current = timeout;
            if (current != null) {
                current.cancel(false);
            }
        }
    }

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

    public SocketIOClient setMaxFragmentBufferBytes(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("maxFragmentBufferBytes must be positive");
        }
        this.maxFragmentBufferBytes = bytes;
        return this;
    }

    
    
    

    public SocketIOClient on(String event, SocketIOClientEventHandler handler) {
        eventHandlers.computeIfAbsent(event, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(handler);
        return this;
    }

    
    public SocketIOClient cookie(String cookieHeader) {
        this.cookieHeader = cookieHeader;
        return this;
    }

    
    
    

    public synchronized boolean connect() {
        if (isConnected()) {
            return true;
        }
        closeResources(new IllegalStateException("Socket.IO reconnecting"), true, null);
        try {
            resourcesClosed.set(false);
            closed = false;
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
            Listener listener = new Listener();
            activeListener = listener;
            ws = builder
                    .buildAsync(URI.create(uri), listener)
                    .get(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
            
            waitForReady();
            if (!connected) {
                closeResources(new TimeoutException("Socket.IO connection timed out"), true, listener);
            }
            return connected;
        } catch (Exception e) {
            closeResources(new IllegalStateException("Socket.IO connection failed", e), true, activeListener);
            log.error("Socket.IO 连接失败: {}:{} ns={}, type={}", host, port, cleanNamespace,
                    e.getClass().getSimpleName());
            return false;
        }
    }

    private void waitForReady() throws InterruptedException {
        if (!readyLatch.await(connectTimeout.toMillis(), TimeUnit.MILLISECONDS) && !connected) {
            log.warn("Socket.IO 连接超时: {}:{}{}", host, port, namespace);
        }
    }

    public void disconnect() {
        closeResources(new IllegalStateException("Socket.IO disconnected"), true, null);
    }

    private void closeResources(Throwable error, boolean closeWebSocket, Listener source) {
        if (source != null && source != activeListener) {
            return;
        }
        if (!resourcesClosed.compareAndSet(false, true)) {
            return;
        }
        closed = true;
        connected = false;
        readyLatch.countDown();
        failPending(error);

        Listener listener = activeListener;
        WebSocket socket = ws;
        HttpClient httpClient = client;
        activeListener = null;
        ws = null;
        client = null;
        sid = null;
        synchronized (sendLock) {
            sendTail = CompletableFuture.completedFuture(null);
        }
        if (listener != null) {
            listener.clearBuffers();
        }
        if (closeWebSocket && socket != null) {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
            }
        }
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception ignored) {
            }
        }
    }

    
    
    

    
    public void emit(String event, Object data) {
        sendFrame(encodeEvent(event, data));
    }

    
    public CompletableFuture<Object> request(String event, Object data) {
        return request(event, data, connectTimeout);
    }

    public CompletableFuture<Object> request(String event, Object data, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        String uuid = IdUtil.uuidShort();
        CompletableFuture<Object> future = new CompletableFuture<>();
        PendingRequest request = new PendingRequest(future);
        pendingRequests.put(uuid, request);
        ScheduledFuture<?> timeoutFuture = REQUEST_TIMEOUT_EXECUTOR.schedule(() -> {
            PendingRequest pending = pendingRequests.remove(uuid);
            if (pending != null) {
                pending.future().completeExceptionally(new TimeoutException("请求超时: " + event));
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        request.timeout(timeoutFuture);
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("uuid", uuid);
        envelope.put("data", data);
        try {
            sendFrame(encodeEvent(event, envelope)).whenComplete((ignored, error) -> {
                if (error != null) {
                    failPending(uuid, new IllegalStateException("Socket.IO request send failed", error));
                }
            });
        } catch (Exception e) {
            failPending(uuid, e);
        }
        return future;
    }

    
    public Object requestBlocking(String event, Object data, long timeoutMs) throws Exception {
        CompletableFuture<Object> future = request(event, data, Duration.ofMillis(timeoutMs));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            completePendingFutureExceptionally(future, new TimeoutException("请求超时: " + event));
            throw new java.util.concurrent.TimeoutException("请求超时: " + event);
        } catch (ExecutionException e) {
            throw new Exception("请求失败: " + event, e.getCause());
        }
    }

    private CompletableFuture<WebSocket> sendFrame(String frame) {
        if (!connected || ws == null) {
            throw new IllegalStateException("Socket.IO 未连接");
        }
        return enqueueText(ws, frame);
    }

    private CompletableFuture<WebSocket> enqueueText(WebSocket socket, String text) {
        synchronized (sendLock) {
            if (socket == null || socket != ws || closed) {
                return CompletableFuture.failedFuture(new IllegalStateException("Socket.IO 未连接"));
            }
            CompletableFuture<WebSocket> sent = sendTail.handle((ignored, previousError) -> null)
                    .thenCompose(ignored -> socket.sendText(text, true));
            sendTail = sent.handle((ignored, error) -> null);
            return sent;
        }
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
            enqueueText(webSocket, text).exceptionally(error -> {
                connectionFailed(new IllegalStateException("Socket.IO send failed", error));
                return null;
            });
        } catch (Exception e) {
            connectionFailed(new IllegalStateException("Socket.IO send failed", e));
            log.debug("Socket.IO 发送失败: bytes={}, summary={}, type={}", utf8Length(text), summarize(text),
                    e.getClass().getSimpleName());
        }
    }

    private void handleOpen(String data, WebSocket webSocket) {
        try {
            JsonNode json = ApiResponse.mapper().readTree(data);
            sid = json.path("sid").asText();
        } catch (Exception e) {
            log.warn("解析 Engine.IO open 失败: bytes={}, summary={}, type={}", utf8Length(data),
                    summarize(data), e.getClass().getSimpleName());
        }
        
        String connectPacket = "40" + (namespace.equals("/") ? "" : namespace + ",");
        safeSendText(webSocket, connectPacket);
        log.debug("Engine.IO 客户端已打开: {}:{} ns={} sid={}", host, port, namespace, sid);
    }

    private void handleSocketIOPacket(String packet) {
        if (packet == null || packet.isEmpty()) {
            return;
        }
        char type = packet.charAt(0);
        String body = packet.length() > 1 ? packet.substring(1) : "";
        switch (type) {
            case '0' -> {
                connected = true;
                readyLatch.countDown();
                log.info("Socket.IO 客户端已连接: {}:{} ns={} sid={}", host, port, namespace, sid);
            }
            case '1' -> connectionFailed(new IllegalStateException("Socket.IO namespace disconnected"));
            case '2' -> handleEvent(body);
            case '4' -> connectionFailed(new IllegalStateException("Socket.IO namespace rejected: " + summarize(body)));
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
                PendingRequest pending = pendingRequests.remove(uuid);
                if (pending != null) {
                    pending.cancelTimeout();
                    pending.future().complete(data);
                    return;
                }
            }

            List<SocketIOClientEventHandler> handlers = eventHandlers.get(event);
            if (handlers != null) {
                handlers.forEach(h -> h.handle(this, event, data));
            }
        } catch (Exception e) {
            log.warn("解析 Socket.IO 客户端事件失败: bytes={}, summary={}, type={}", utf8Length(payload),
                    summarize(payload), e.getClass().getSimpleName());
        }
    }

    
    
    

    final class Listener implements WebSocket.Listener {

        private StringBuilder textBuffer = new StringBuilder();
        private int textBufferBytes;
        private int binaryBufferBytes;
        private char pendingHighSurrogate;

        private void clearBuffers() {
            textBuffer = new StringBuilder();
            textBufferBytes = 0;
            binaryBufferBytes = 0;
            pendingHighSurrogate = 0;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            
            ws = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            int fragmentBytes = countTextFragmentBytes(data, last);
            if ((long) textBufferBytes + fragmentBytes > maxFragmentBufferBytes) {
                textBuffer = new StringBuilder();
                textBufferBytes = 0;
                pendingHighSurrogate = 0;
                connectionFailed(new IllegalStateException("Socket.IO text fragment buffer limit exceeded"));
                webSocket.sendClose(1009, "message too large");
                webSocket.request(1);
                return null;
            }
            if (data != null) {
                textBuffer.append(data);
            }
            textBufferBytes += fragmentBytes;
            if (last) {
                handleFrame(textBuffer.toString(), webSocket);
                textBuffer = new StringBuilder();
                textBufferBytes = 0;
            }
            webSocket.request(1);
            return null;
        }

        private int countTextFragmentBytes(CharSequence data, boolean last) {
            int bytes = 0;
            int index = 0;
            int length = data == null ? 0 : data.length();
            if (pendingHighSurrogate != 0) {
                if (length > 0 && Character.isLowSurrogate(data.charAt(0))) {
                    bytes += 4;
                    index = 1;
                    pendingHighSurrogate = 0;
                } else if (length > 0 || last) {
                    bytes++;
                    pendingHighSurrogate = 0;
                }
            }
            while (index < length) {
                char current = data.charAt(index++);
                if (current <= 0x7f) {
                    bytes++;
                } else if (current <= 0x7ff) {
                    bytes += 2;
                } else if (Character.isHighSurrogate(current)) {
                    if (index < length && Character.isLowSurrogate(data.charAt(index))) {
                        bytes += 4;
                        index++;
                    } else if (index == length && !last) {
                        pendingHighSurrogate = current;
                    } else {
                        bytes++;
                    }
                } else if (Character.isLowSurrogate(current)) {
                    bytes++;
                } else {
                    bytes += 3;
                }
            }
            if (last && pendingHighSurrogate != 0) {
                bytes++;
                pendingHighSurrogate = 0;
            }
            return bytes;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            int fragmentBytes = data == null ? 0 : data.remaining();
            if ((long) binaryBufferBytes + fragmentBytes > maxFragmentBufferBytes) {
                binaryBufferBytes = 0;
                connectionFailed(new IllegalStateException("Socket.IO binary fragment buffer limit exceeded"));
                webSocket.sendClose(1009, "message too large");
            } else if (last) {
                binaryBufferBytes = 0;
            } else {
                binaryBufferBytes += fragmentBytes;
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeResources(new IllegalStateException("Socket.IO connection closed: " + statusCode), false, this);
            log.info("Socket.IO 客户端连接关闭: {}:{}, status={}, reason={}", host, port, statusCode,
                    summarize(reason));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closeResources(error == null ? new IllegalStateException("Socket.IO connection error") : error,
                    true, this);
            String message = error == null ? null : error.getMessage();
            log.error("Socket.IO 客户端错误: {}:{}, type={}, messageBytes={}, messageSummary={}", host, port,
                    error == null ? "null" : error.getClass().getSimpleName(), utf8Length(message),
                    summarize(message));
        }
    }

    private void connectionFailed(Throwable error) {
        closeResources(error, true, activeListener);
    }

    private void failPending(String uuid, Throwable error) {
        PendingRequest pending = pendingRequests.remove(uuid);
        if (pending != null) {
            pending.cancelTimeout();
            pending.future().completeExceptionally(error);
        }
    }

    private void completePendingFutureExceptionally(CompletableFuture<Object> future, Throwable error) {
        pendingRequests.entrySet().removeIf(entry -> {
            if (entry.getValue().future() != future) {
                return false;
            }
            entry.getValue().cancelTimeout();
            future.completeExceptionally(error);
            return true;
        });
    }

    private void failPending(Throwable error) {
        pendingRequests.forEach((uuid, pending) -> failPending(uuid, error));
    }

    private static String summarize(String value) {
        if (value == null) {
            return "null";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ');
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128) + "...";
    }

    private static int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
