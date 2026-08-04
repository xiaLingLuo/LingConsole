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
package im.xz.cn.lingconsole.app.panel.service;

import com.fasterxml.jackson.databind.JsonNode;
import im.xz.cn.lingconsole.app.panel.model.Node;
import im.xz.cn.lingconsole.app.panel.remote.DaemonConnection;
import im.xz.cn.lingconsole.app.panel.repository.NodeRepository;
import im.xz.cn.lingconsole.app.panel.util.NodeUrlUtil;
import im.xz.cn.lingconsole.common.util.ErrorMessageUtil;
import im.xz.cn.lingconsole.common.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class NodeService {

    private static final Logger log = LoggerFactory.getLogger(NodeService.class);
    private final NodeRepository nodeRepository;
    private final Map<String, DaemonConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, Long> failedCooldowns = new ConcurrentHashMap<>();
    private static final long FAILED_COOLDOWN_MS = 5000;
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "node-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public NodeService(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }
    public void start() {
        heartbeatExecutor.scheduleAtFixedRate(this::checkAllNodes, 5, 30, TimeUnit.SECONDS);
    }
    public void stop() {
        heartbeatExecutor.shutdownNow();
        connections.values().forEach(DaemonConnection::disconnect);
        connections.clear();
    }

    
    
    

    public List<Node> list() {
        return nodeRepository.findAll();
    }

    public Node create(String id, String name, String url, String key) {
        if (id == null || !id.matches("[a-z0-9]+")) {
            throw new IllegalArgumentException("节点 ID 仅允许小写英文字母和阿拉伯数字");
        }
        if (nodeRepository.findById(id).isPresent()) {
            throw new IllegalArgumentException("节点 ID 已存在: " + id);
        }
        Node node = new Node();
        node.setId(id);
        node.setName(name);
        node.setUrl(NodeUrlUtil.normalize(url));
        node.setKey(key);
        node.setStatus(Node.STATUS_OFFLINE);
        long now = System.currentTimeMillis() / 1000;
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        nodeRepository.insert(node);
        
        return node;
    }

    public Node update(String id, String name, String url, String key) {
        Node node = nodeRepository.findById(id).orElse(null);
        if (node == null) {
            return null;
        }
        node.setName(name);
        node.setUrl(NodeUrlUtil.normalize(url));
        
        if (key != null && !key.isBlank()) {
            node.setKey(key);
        }
        nodeRepository.update(node);
        
        DaemonConnection conn = connections.remove(id);
        if (conn != null) {
            conn.disconnect();
        }
        connectNode(id);
        return node;
    }

    public boolean delete(String id) {
        DaemonConnection conn = connections.remove(id);
        if (conn != null) {
            conn.disconnect();
        }
        nodeRepository.delete(id);
        return true;
    }

    
    public Node updateStyle(String id, String style) {
        Node node = nodeRepository.findById(id).orElse(null);
        if (node == null) {
            return null;
        }
        node.setStyle(style);
        nodeRepository.updateStyle(id, style);
        return node;
    }

    public Optional<Node> findById(String id) {
        return nodeRepository.findById(id);
    }

    
    
    

    public DaemonConnection getConnection(String nodeId) {
        DaemonConnection conn = connections.get(nodeId);
        if (conn != null && conn.isConnected()) {
            return conn;
        }
        return connectNode(nodeId);
    }

    private synchronized DaemonConnection connectNode(String nodeId) {
        try {
            
            Long lastFailed = failedCooldowns.get(nodeId);
            if (lastFailed != null && System.currentTimeMillis() - lastFailed < FAILED_COOLDOWN_MS) {
                return null;
            }
            Node node = nodeRepository.findById(nodeId).orElse(null);
            if (node == null) {
                return null;
            }
            DaemonConnection conn = new DaemonConnection(node);
            boolean ok = conn.connect();
            node.setStatus(ok ? Node.STATUS_ONLINE : Node.STATUS_OFFLINE);
            nodeRepository.updateStatus(nodeId, node.getStatus());
            if (ok) {
                connections.put(nodeId, conn);
                failedCooldowns.remove(nodeId);
            } else {
                connections.remove(nodeId);
                failedCooldowns.put(nodeId, System.currentTimeMillis());
                conn.disconnect();
            }
            return ok ? conn : null;
        } catch (Exception e) {
            log.warn("连接节点失败: {}", nodeId, e);
            connections.remove(nodeId);
            failedCooldowns.put(nodeId, System.currentTimeMillis());
            return null;
        }
    }

    public void checkNode(String nodeId) {
        Node node = nodeRepository.findById(nodeId).orElse(null);
        if (node == null) {
            return;
        }
        DaemonConnection conn = connections.get(nodeId);
        boolean online = conn != null && conn.isConnected();
        if (node.getStatus() != (online ? Node.STATUS_ONLINE : Node.STATUS_OFFLINE)) {
            nodeRepository.updateStatus(nodeId, online ? Node.STATUS_ONLINE : Node.STATUS_OFFLINE);
        }
        if (!online) {
            connectNode(nodeId);
        }
    }

    private void checkAllNodes() {
        List<Node> nodes = nodeRepository.findAll();
        nodes.forEach(n -> {
            try {
                checkNode(n.getId());
            } catch (Exception e) {
                log.debug("节点心跳异常: {}", n.getName(), e);
            }
        });
    }

    
    public Node ensureLocalDaemon(String name, String url, String key) {
        Optional<Node> existing = nodeRepository.findByName(name);
        Node node;
        if (existing.isPresent()) {
            node = existing.get();
            node.setUrl(NodeUrlUtil.normalize(url));
            node.setKey(key);
            nodeRepository.update(node);
        } else {
            node = create(randomNodeId(), name, url, key);
            connectNode(node.getId());
            return node;
        }
        connectNode(node.getId());
        return node;
    }

    
    private String randomNodeId() {
        String base = IdUtil.uuid().replace("-", "").toLowerCase();
        String candidate = base.substring(0, 12);
        while (nodeRepository.findById(candidate).isPresent()) {
            candidate = IdUtil.uuid().replace("-", "").toLowerCase().substring(0, 12);
        }
        return candidate;
    }

    
    public Object systemInfo(String nodeId) {
        DaemonConnection conn = getConnection(nodeId);
        if (conn == null) {
            throw new IllegalStateException("节点离线, 请确保 55700 端口可被访问");
        }
        try {
            Object resp = conn.requestBlocking("system:info", Map.of(), 5000);
            if (resp instanceof JsonNode n) {
                return n.path("data");
            }
            return null;
        } catch (Exception e) {
            throw new IllegalStateException(ErrorMessageUtil.with("获取系统信息失败", e));
        }
    }
}
