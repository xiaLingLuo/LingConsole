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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class SocketIOServer {

    private static final Logger log = LoggerFactory.getLogger(SocketIOServer.class);

    
    private static final int PING_INTERVAL = 60_000;
    private static final int PING_TIMEOUT = 40_000;

    private final Map<String, SocketIOSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, NamespaceHandler> namespaces = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> heartbeatFutures = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "socketio-heartbeat");
        t.setDaemon(true);
        return t;
    });

    
    private static final class NamespaceHandler {
        final String namespace;
        SocketIOConnectHandler connectHandler;
        final Map<String, SocketIOEventHandler> handlers = new ConcurrentHashMap<>();

        NamespaceHandler(String namespace) {
            this.namespace = namespace;
        }

        void dispatch(SocketIOConnection conn, String event, Object data) {
            SocketIOEventHandler handler = handlers.get(event);
            if (handler != null) {
                handler.handle(conn, event, data);
            } else {
                log.debug("未注册事件: {} @ {}", event, namespace);
            }
        }
    }

    
    @FunctionalInterface
    public interface SocketIODisconnectHandler {
        void onDisconnect(String sessionId);
    }

    private volatile SocketIODisconnectHandler disconnectHandler;

    
    
    

    
    public SocketIOServer registerNamespace(String namespace) {
        namespaces.computeIfAbsent(namespace, NamespaceHandler::new);
        return this;
    }

    
    public SocketIOServer on(String namespace, String event, SocketIOEventHandler handler) {
        NamespaceHandler ns = namespaces.computeIfAbsent(namespace, NamespaceHandler::new);
        ns.handlers.put(event, handler);
        return this;
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
                () -> ctx.closeSession(1000, "bye"));
        session.setCookies(ctx.cookieMap());
        sessions.put(sid, session);

        
        String open = "0{\"sid\":\"" + sid + "\",\"upgrades\":[],\"pingInterval\":"
                + PING_INTERVAL + ",\"pingTimeout\":" + PING_TIMEOUT + ",\"maxPayload\":104857600}";
        session.send(open);

        
        java.util.concurrent.ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (session.isClosed()) {
                return;
            }
            long idle = System.currentTimeMillis() - session.lastActivity();
            if (idle > PING_INTERVAL + PING_TIMEOUT) {
                log.warn("心跳超时, 断开连接: {}", sid);
                session.close();
            } else {
                session.send("2"); 
            }
        }, PING_INTERVAL, PING_INTERVAL, TimeUnit.MILLISECONDS);
        heartbeatFutures.put(sid, heartbeat);

        log.info("WebSocket 已连接: sid={}", sid);
    }

    private void onWsMessage(WsMessageContext ctx) {
        SocketIOSession session = sessions.get(ctx.sessionId());
        if (session != null) {
            handleEngineIOPacket(session, ctx.message());
        }
    }

    private void onWsBinaryMessage(WsBinaryMessageContext ctx) {
        
        log.debug("忽略二进制帧: sid={}", ctx.sessionId());
    }

    private void onWsClose(WsCloseContext ctx) {
        cleanupSession(ctx.sessionId());
    }

    private void onWsError(WsErrorContext ctx) {
        log.debug("WebSocket 错误: sid={}, error={}", ctx.sessionId(),
                ctx.error() == null ? "null" : ctx.error().getMessage());
        cleanupSession(ctx.sessionId());
    }

    private void cleanupSession(String sid) {
        SocketIOSession session = sessions.remove(sid);
        if (session != null) {
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
    }

    
    public void stop() {
        heartbeatExecutor.shutdownNow();
        heartbeatFutures.clear();
        sessions.values().forEach(SocketIOSession::markClosed);
        sessions.clear();
    }

    
    
    

    private void handleEngineIOPacket(SocketIOSession session, String packet) {
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
        session.bind(namespace);
        
        session.send("40" + namespacePrefix(namespace));
        if (ns.connectHandler != null) {
            ns.connectHandler.onConnect(new SocketIOConnection(this, session, namespace), query);
        }
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
        if (ns == null) {
            return;
        }
        try {
            JsonNode arr = ApiResponse.mapper().readTree(payload);
            if (!arr.isArray() || arr.isEmpty()) {
                log.warn("无效事件负载: {}", payload);
                return;
            }
            String eventName = arr.get(0).asText();
            Object data = arr.size() > 1 ? arr.get(1) : null;
            ns.dispatch(new SocketIOConnection(this, session, namespace), eventName, data);
        } catch (Exception e) {
            log.error("解析 Socket.IO 事件失败: {}", payload, e);
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
        String socketPacket = "4" + namespacePrefix(namespace) + message;
        return "4" + socketPacket;
    }

    private String namespacePrefix(String namespace) {
        return namespace == null || namespace.equals("/") ? "" : namespace + ",";
    }
}
