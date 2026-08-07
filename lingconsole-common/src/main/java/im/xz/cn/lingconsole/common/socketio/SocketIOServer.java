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
import im.xz.cn.lingconsole.common.config.Constants;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import io.javalin.config.RoutesConfig;
import io.javalin.websocket.WsBinaryMessageContext;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsErrorContext;
import io.javalin.websocket.WsMessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class SocketIOServer {

    private static final Logger log = LoggerFactory.getLogger(SocketIOServer.class);

    
    private static final int PING_INTERVAL = 25_000;
    private static final int PING_TIMEOUT = 20_000;
    private static final String UNKNOWN_IP = "<unknown>";
    public static final String CORE_EVENT_OWNER = "lingconsole:core";

    private volatile long authenticationTimeoutMillis = 10_000;
    private volatile int maxTextMessageBytes = 1024 * 1024;
    private volatile int maxBinaryMessageBytes = 1024 * 1024;
    private volatile int maxAggregatedMessageBytes = 4 * 1024 * 1024;
    private volatile int maxUnauthenticatedConnectionsPerIp = 20;
    private volatile int maxConnections = 10_000;
    private volatile int maxEventsPerSession = 100;
    private volatile long eventRateWindowMillis = 1_000;

    private final Map<String, SocketIOSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, NamespaceHandler> namespaces = new ConcurrentHashMap<>();
    private final AtomicReference<Map<EventKey, OwnedEventHandler>> eventHandlers =
            new AtomicReference<>(Map.of());
    private final Set<EventKey> unauthenticatedEvents = ConcurrentHashMap.newKeySet();
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> heartbeatFutures = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> authenticationFutures = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> unauthenticatedByIp = new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger();

    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "socketio-heartbeat");
        t.setDaemon(true);
        return t;
    });

    
    private static final class NamespaceHandler {
        final String namespace;
        SocketIOConnectHandler connectHandler;

        NamespaceHandler(String namespace) {
            this.namespace = namespace;
        }
    }

    private record EventKey(String namespace, String event) {
    }

    private record OwnedEventHandler(String owner, SocketIOEventHandler handler) {
    }

    
    @FunctionalInterface
    public interface SocketIODisconnectHandler {
        void onDisconnect(String sessionId);
    }

    private volatile SocketIODisconnectHandler disconnectHandler;

    
    private volatile java.util.function.BiPredicate<String, String> originValidator;

    
    public void setOriginValidator(java.util.function.BiPredicate<String, String> validator) {
        this.originValidator = validator;
    }

    public SocketIOServer setAuthenticationTimeout(Duration timeout) {
        this.authenticationTimeoutMillis = positiveDuration(timeout, "authenticationTimeout");
        return this;
    }

    public SocketIOServer setMaxTextMessageBytes(int bytes) {
        this.maxTextMessageBytes = positive(bytes, "maxTextMessageBytes");
        return this;
    }

    public SocketIOServer setMaxBinaryMessageBytes(int bytes) {
        this.maxBinaryMessageBytes = positive(bytes, "maxBinaryMessageBytes");
        return this;
    }

    public SocketIOServer setMaxAggregatedMessageBytes(int bytes) {
        this.maxAggregatedMessageBytes = positive(bytes, "maxAggregatedMessageBytes");
        return this;
    }

    public SocketIOServer setMaxUnauthenticatedConnectionsPerIp(int limit) {
        this.maxUnauthenticatedConnectionsPerIp = positive(limit, "maxUnauthenticatedConnectionsPerIp");
        return this;
    }

    public SocketIOServer setMaxConnections(int limit) {
        this.maxConnections = positive(limit, "maxConnections");
        return this;
    }

    public SocketIOServer setEventRateLimit(int events, Duration window) {
        this.maxEventsPerSession = positive(events, "events");
        this.eventRateWindowMillis = positiveDuration(window, "window");
        return this;
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static long positiveDuration(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        long millis = value.toMillis();
        if (millis <= 0) {
            throw new IllegalArgumentException(name + " must be at least one millisecond");
        }
        return millis;
    }

    
    
    

    
    public SocketIOServer registerNamespace(String namespace) {
        namespaces.computeIfAbsent(namespace, NamespaceHandler::new);
        return this;
    }

    
    public SocketIOServer on(String namespace, String event, SocketIOEventHandler handler) {
        registerEvent(CORE_EVENT_OWNER, namespace, event, handler);
        return this;
    }

    public SocketIOServer on(String owner, String namespace, String event, SocketIOEventHandler handler) {
        if (CORE_EVENT_OWNER.equals(owner)) {
            throw new IllegalArgumentException("The core event owner is reserved");
        }
        registerEvent(owner, namespace, event, handler);
        return this;
    }

    public SocketIOServer allowUnauthenticatedEvent(String namespace, String event) {
        unauthenticatedEvents.add(eventKey(namespace, event));
        return this;
    }

    public int unregisterOwner(String owner) {
        Objects.requireNonNull(owner, "owner");
        if (CORE_EVENT_OWNER.equals(owner)) {
            throw new IllegalArgumentException("The core event owner cannot be unregistered");
        }
        while (true) {
            Map<EventKey, OwnedEventHandler> current = eventHandlers.get();
            Map<EventKey, OwnedEventHandler> updated = new HashMap<>(current);
            int before = updated.size();
            updated.entrySet().removeIf(entry -> owner.equals(entry.getValue().owner()));
            int removed = before - updated.size();
            if (removed == 0 || eventHandlers.compareAndSet(current, Map.copyOf(updated))) {
                return removed;
            }
        }
    }

    private void registerEvent(String owner, String namespace, String event, SocketIOEventHandler handler) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner is required");
        }
        Objects.requireNonNull(handler, "handler");
        EventKey key = eventKey(namespace, event);
        namespaces.computeIfAbsent(namespace, NamespaceHandler::new);
        while (true) {
            Map<EventKey, OwnedEventHandler> current = eventHandlers.get();
            OwnedEventHandler existing = current.get(key);
            if (existing != null && !existing.owner().equals(owner)) {
                throw new IllegalStateException("Socket.IO event already registered by " + existing.owner()
                        + ": " + namespace + "/" + event);
            }
            Map<EventKey, OwnedEventHandler> updated = new HashMap<>(current);
            updated.put(key, new OwnedEventHandler(owner, handler));
            if (eventHandlers.compareAndSet(current, Map.copyOf(updated))) {
                return;
            }
        }
    }

    private static EventKey eventKey(String namespace, String event) {
        if (namespace == null || namespace.isBlank() || event == null || event.isBlank()) {
            throw new IllegalArgumentException("namespace and event are required");
        }
        return new EventKey(namespace, event);
    }

    
    public SocketIOServer onConnect(String namespace, SocketIOConnectHandler handler) {
        NamespaceHandler ns = namespaces.computeIfAbsent(namespace, NamespaceHandler::new);
        ns.connectHandler = handler;
        return this;
    }

    
    public SocketIOServer onDisconnect(SocketIODisconnectHandler handler) {
        this.disconnectHandler = handler;
        return this;
    }

    
    public void register(RoutesConfig routes) {
        routes.ws(Constants.SOCKET_IO_PATH, ws -> {
            ws.onConnect(this::onWsConnect);
            ws.onMessage(this::onWsMessage);
            ws.onBinaryMessage(this::onWsBinaryMessage);
            ws.onClose(this::onWsClose);
            ws.onError(this::onWsError);
        });
        log.info("Socket.IO 端点已挂载: {}", Constants.SOCKET_IO_PATH);
    }

    
    
    

    
    public void emit(String namespace, String event, Object data) {
        String frame = encodeEvent(namespace, event, data);
        sessions.values().forEach(s -> {
            if (!s.isClosed() && s.boundNamespaces().contains(namespace)) {
                s.send(frame);
            }
        });
    }

    
    public void sendTo(SocketIOSession session, String namespace, String event, Object data) {
        if (!session.isClosed()) {
            session.send(encodeEvent(namespace, event, data));
        }
    }

    
    
    

    private void onWsConnect(WsConnectContext ctx) {
        String sid = ctx.sessionId();
        SocketIOSession session = new SocketIOSession(
                sid,
                ctx::send,
                ctx::closeSession);
        session.setCookies(ctx.cookieMap());
        session.setOrigin(ctx.header("Origin"));
        session.setRemoteIp(resolveRemoteIp(ctx));

        if (originValidator != null) {
            String origin = session.origin();
            if (origin != null && !origin.isBlank()) {
                String host = ctx.host();
                if (!originValidator.test(origin, host)) {
                    log.warn("WebSocket Origin 校验失败, 拒绝连接: sid={}, originBytes={}, originSummary={}",
                            sid, utf8Length(origin), summarize(origin));
                    session.close();
                    return;
                }
            }
        }

        if (!admitSession(session)) {
            return;
        }

        String open = "0{\"sid\":\"" + sid + "\",\"upgrades\":[],\"pingInterval\":"
                + PING_INTERVAL + ",\"pingTimeout\":" + PING_TIMEOUT + ",\"maxPayload\":"
                + maxAggregatedMessageBytes + "}";
        session.send(open);

        scheduleAuthenticationTimeout(session);

        
        java.util.concurrent.ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (session.isClosed()) {
                return;
            }
            long idle = System.currentTimeMillis() - session.lastPongAt();
            if (idle > PING_INTERVAL + PING_TIMEOUT) {
                log.warn("心跳 PONG 超时, 断开连接: {}", sid);
                session.close();
            } else {
                session.send("2"); 
            }
        }, PING_INTERVAL, PING_INTERVAL, TimeUnit.MILLISECONDS);
        heartbeatFutures.put(sid, heartbeat);

        log.info("WebSocket 已连接: sid={}", sid);
    }

    boolean admitSession(SocketIOSession session) {
        if (connectionCount.incrementAndGet() > maxConnections) {
            connectionCount.decrementAndGet();
            log.warn("WebSocket 全局连接数超限: limit={}", maxConnections);
            session.close(1013, "connection limit exceeded");
            return false;
        }
        String remoteIp = normalizedIp(session.remoteIp());
        AtomicInteger unauthenticated = unauthenticatedByIp.computeIfAbsent(remoteIp, ignored -> new AtomicInteger());
        if (unauthenticated.incrementAndGet() > maxUnauthenticatedConnectionsPerIp) {
            connectionCount.decrementAndGet();
            decrementUnauthenticated(remoteIp);
            log.warn("WebSocket 未认证连接数超限: ip={}, limit={}", remoteIp,
                    maxUnauthenticatedConnectionsPerIp);
            session.close(1008, "unauthenticated connection limit exceeded");
            return false;
        }
        SocketIOSession existing = sessions.putIfAbsent(session.sid(), session);
        if (existing != null) {
            connectionCount.decrementAndGet();
            decrementUnauthenticated(remoteIp);
            session.close(1011, "duplicate session");
            return false;
        }
        return true;
    }

    void scheduleAuthenticationTimeout(SocketIOSession session) {
        authenticationFutures.put(session.sid(), heartbeatExecutor.schedule(() -> {
            if (!session.isClosed() && !session.isAuthenticated()) {
                log.warn("Socket.IO 认证超时: sid={}, ip={}", session.sid(), normalizedIp(session.remoteIp()));
                session.close(1008, "authentication timeout");
            }
        }, authenticationTimeoutMillis, TimeUnit.MILLISECONDS));
    }

    private String resolveRemoteIp(WsConnectContext ctx) {
        try {
            java.net.SocketAddress remote = ctx.session.getRemoteSocketAddress();
            if (remote instanceof java.net.InetSocketAddress inet && inet.getAddress() != null) {
                String ip = inet.getAddress().getHostAddress();
                if (ip != null && !ip.isBlank()) {
                    return ip;
                }
            }
        } catch (Exception e) {
            log.debug("解析 WebSocket 对端 IP 失败", e);
        }
        return null;
    }

    private void onWsMessage(WsMessageContext ctx) {
        SocketIOSession session = sessions.get(ctx.sessionId());
        if (session != null) {
            acceptTextMessage(session, ctx.message());
        }
    }

    private void onWsBinaryMessage(WsBinaryMessageContext ctx) {
        SocketIOSession session = sessions.get(ctx.sessionId());
        if (session == null) {
            return;
        }
        ByteBuffer data = ctx.data();
        int bytes = data == null ? 0 : data.remaining();
        acceptBinaryMessage(session, bytes);
    }

    void acceptBinaryMessage(SocketIOSession session, int bytes) {
        if (bytes > maxBinaryMessageBytes || bytes > maxAggregatedMessageBytes) {
            closeForMessageLimit(session, "binary", bytes,
                    Math.min(maxBinaryMessageBytes, maxAggregatedMessageBytes));
            return;
        }
        log.debug("忽略二进制帧: sid={}, bytes={}", session.sid(), bytes);
    }

    private void onWsClose(WsCloseContext ctx) {
        cleanupSession(ctx.sessionId());
    }

    private void onWsError(WsErrorContext ctx) {
        Throwable error = ctx.error();
        String message = error == null ? null : error.getMessage();
        log.debug("WebSocket 错误: sid={}, type={}, messageBytes={}, messageSummary={}", ctx.sessionId(),
                error == null ? "null" : error.getClass().getSimpleName(), utf8Length(message), summarize(message));
        cleanupSession(ctx.sessionId());
    }

    private void cleanupSession(String sid) {
        SocketIOSession session = sessions.remove(sid);
        if (session != null) {
            connectionCount.decrementAndGet();
            if (!session.isAuthenticated()) {
                decrementUnauthenticated(normalizedIp(session.remoteIp()));
            }
            session.markClosed();
            SocketIODisconnectHandler handler = disconnectHandler;
            if (handler != null) {
                try {
                    handler.onDisconnect(sid);
                } catch (Exception e) {
                    log.debug("断开回调异常", e);
                }
            }
            log.info("WebSocket 已断开: sid={}", sid);
        }
        java.util.concurrent.ScheduledFuture<?> future = heartbeatFutures.remove(sid);
        if (future != null) {
            future.cancel(false);
        }
        java.util.concurrent.ScheduledFuture<?> authFuture = authenticationFutures.remove(sid);
        if (authFuture != null) {
            authFuture.cancel(false);
        }
    }

    
    public void stop() {
        heartbeatExecutor.shutdownNow();
        heartbeatFutures.clear();
        authenticationFutures.clear();
        sessions.values().forEach(SocketIOSession::markClosed);
        sessions.clear();
        connectionCount.set(0);
        unauthenticatedByIp.clear();
        eventHandlers.set(Map.of());
        unauthenticatedEvents.clear();
    }

    
    
    

    void acceptTextMessage(SocketIOSession session, String packet) {
        int bytes = utf8Length(packet);
        if (bytes > maxTextMessageBytes || bytes > maxAggregatedMessageBytes) {
            closeForMessageLimit(session, "text", bytes,
                    Math.min(maxTextMessageBytes, maxAggregatedMessageBytes));
            return;
        }
        handleEngineIOPacket(session, packet);
    }

    void handleEngineIOPacket(SocketIOSession session, String packet) {
        if (packet == null || packet.isEmpty()) {
            return;
        }
        
        session.markActivity();
        char type = packet.charAt(0);
        String data = packet.length() > 1 ? packet.substring(1) : "";
        switch (type) {
            case '2' -> session.send("3");            
            case '3' -> session.markPong();           
            case '4' -> handleSocketIOPacket(session, data); 
            case '1' -> session.close();              
            case '5', '6' -> {  }
            default -> log.debug("未知 Engine.IO 数据包: {}", type);
        }
    }

    
    
    

    private void handleSocketIOPacket(SocketIOSession session, String packet) {
        if (packet == null || packet.isEmpty()) {
            return;
        }
        char type = packet.charAt(0);
        String body = packet.length() > 1 ? packet.substring(1) : "";
        switch (type) {
            case '0' -> handleConnect(session, body);
            case '1' -> handleDisconnect(session, body);
            case '2' -> handleEvent(session, body);
            case '5', '6' -> {  }
            default -> log.debug("忽略 Socket.IO 数据包: {}", type);
        }
    }

    private void handleConnect(SocketIOSession session, String body) {
        String[] parts = splitNamespace(body);
        String namespace = parts[0];
        String query = parts[1] == null || parts[1].isEmpty() ? null : parts[1];

        
        if (namespace.contains("?")) {
            int q = namespace.indexOf('?');
            String qs = namespace.substring(q + 1);
            namespace = namespace.substring(0, q);
            query = query == null ? qs : qs + "&" + query;
        }

        NamespaceHandler ns = namespaces.get(namespace);
        if (ns == null) {
            session.send(encodeError(namespace, "Invalid namespace: " + namespace));
            return;
        }
        if (session.hasNamespace(namespace)) {
            return;
        }
        SocketIOConnectResult result = SocketIOConnectResult.accept();
        if (ns.connectHandler != null) {
            try {
                result = ns.connectHandler.onConnect(new SocketIOConnection(this, session, namespace), query);
            } catch (Exception e) {
                log.warn("Socket.IO namespace 认证异常: sid={}, ns={}, type={}", session.sid(), namespace,
                        e.getClass().getSimpleName());
                result = SocketIOConnectResult.reject("Connection authorization failed");
            }
        }
        if (result == null || !result.accepted() || session.isClosed()) {
            if (!session.isClosed()) {
                session.send(encodeError(namespace, result == null ? "Connection rejected" : result.message()));
            }
            return;
        }
        session.bind(namespace);
        session.send("40" + namespacePrefix(namespace));
        log.info("Socket.IO 已连接命名空间: sid={}, ns={}", session.sid(), namespace);
    }

    private void handleDisconnect(SocketIOSession session, String body) {
        String[] parts = splitNamespace(body);
        session.unbind(parts[0]);
        if (parts[0].equals("/")) {
            session.close();
        }
    }

    private void handleEvent(SocketIOSession session, String body) {
        String[] parts = splitNamespace(body);
        String namespace = parts[0];
        String payload = parts[1];
        NamespaceHandler ns = namespaces.get(namespace);
        if (ns == null || !session.hasNamespace(namespace)) {
            log.debug("忽略未绑定 namespace 的事件: sid={}, ns={}", session.sid(), namespace);
            return;
        }
        if (!session.allowEvent(maxEventsPerSession, eventRateWindowMillis)) {
            log.warn("Socket.IO 会话事件速率超限: sid={}, limit={}, windowMs={}", session.sid(),
                    maxEventsPerSession, eventRateWindowMillis);
            session.close(1008, "event rate limit exceeded");
            return;
        }
        try {
            JsonNode arr = ApiResponse.mapper().readTree(payload);
            if (!arr.isArray() || arr.isEmpty()) {
                log.warn("无效事件负载: bytes={}, summary={}", utf8Length(payload), summarize(payload));
                return;
            }
            String eventName = arr.get(0).asText();
            Object data = arr.size() > 1 ? arr.get(1) : null;
            EventKey key = new EventKey(namespace, eventName);
            if (!session.isAuthenticated() && !unauthenticatedEvents.contains(key)) {
                log.debug("忽略未认证事件: sid={}, ns={}, eventSummary={}", session.sid(), namespace,
                        summarize(eventName));
                new SocketIOConnection(this, session, namespace)
                        .emit(eventName, SocketIOResponse.error(data, 401, "未认证"));
                return;
            }
            OwnedEventHandler ownedHandler = eventHandlers.get().get(key);
            if (ownedHandler != null) {
                ownedHandler.handler().handle(new SocketIOConnection(this, session, namespace), eventName, data);
            } else {
                log.debug("未注册事件: eventBytes={}, eventSummary={}, ns={}", utf8Length(eventName),
                        summarize(eventName), summarize(namespace));
            }
        } catch (Exception e) {
            log.warn("解析 Socket.IO 事件失败: bytes={}, summary={}, type={}", utf8Length(payload),
                    summarize(payload), e.getClass().getSimpleName());
        }
    }

    
    private String[] splitNamespace(String body) {
        if (body == null || body.isEmpty()) {
            return new String[]{"/", ""};
        }
        if (body.startsWith("/")) {
            int comma = body.indexOf(',');
            if (comma == -1) {
                return new String[]{body, ""};
            }
            return new String[]{body.substring(0, comma), body.substring(comma + 1)};
        }
        return new String[]{"/", body};
    }

    
    
    

    
    private String encodeEvent(String namespace, String event, Object data) {
        ArrayNode arr = ApiResponse.mapper().createArrayNode();
        arr.add(event);
        if (data != null) {
            arr.addPOJO(data);
        }
        String socketPacket = "2" + namespacePrefix(namespace) + arr;
        return "4" + socketPacket;
    }

    private String encodeError(String namespace, String message) {
        com.fasterxml.jackson.databind.node.ObjectNode error = ApiResponse.mapper().createObjectNode();
        error.put("message", message == null ? "Connection rejected" : message);
        String socketPacket = "4" + namespacePrefix(namespace) + error;
        return "4" + socketPacket;
    }

    private String namespacePrefix(String namespace) {
        return namespace == null || namespace.equals("/") ? "" : namespace + ",";
    }

    boolean markAuthenticated(SocketIOSession session) {
        if (!session.markAuthenticated()) {
            return false;
        }
        decrementUnauthenticated(normalizedIp(session.remoteIp()));
        java.util.concurrent.ScheduledFuture<?> future = authenticationFutures.remove(session.sid());
        if (future != null) {
            future.cancel(false);
        }
        return true;
    }

    private void decrementUnauthenticated(String ip) {
        unauthenticatedByIp.computeIfPresent(ip, (ignored, count) ->
                count.decrementAndGet() <= 0 ? null : count);
    }

    private static String normalizedIp(String ip) {
        return ip == null || ip.isBlank() ? UNKNOWN_IP : ip;
    }

    private void closeForMessageLimit(SocketIOSession session, String kind, int bytes, int limit) {
        log.warn("Socket.IO {} 消息超限: sid={}, bytes={}, limit={}", kind, session.sid(), bytes, limit);
        session.close(1009, "message too large");
    }

    private static int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String summarize(String value) {
        if (value == null) {
            return "null";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ');
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128) + "...";
    }
}
