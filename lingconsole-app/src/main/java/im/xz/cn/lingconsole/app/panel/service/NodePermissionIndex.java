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
import im.xz.cn.lingconsole.app.panel.repository.AppIdRegistryRepository;
import im.xz.cn.lingconsole.app.panel.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class NodePermissionIndex {

    private static final Logger log = LoggerFactory.getLogger(NodePermissionIndex.class);

    public record AppRef(String id, String name) {
    }

    private final NodeRepository nodeRepository;
    private final NodeService nodeService;
    private final AppIdRegistryRepository appIdRegistry;
    private static volatile NodePermissionIndex activeInstance;
    private volatile boolean stopped;

    private volatile List<Node> nodes = List.of();
    private volatile Map<String, List<AppRef>> nodeApps = Map.of();
    private final java.util.Set<String> synchronizedNodes = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "node-perm-index");
        t.setDaemon(true);
        return t;
    });

    public NodePermissionIndex(NodeRepository nodeRepository, NodeService nodeService) {
        this.nodeRepository = nodeRepository;
        this.nodeService = nodeService;
        this.appIdRegistry = new AppIdRegistryRepository(nodeRepository.databaseManager());
        activeInstance = this;
    }

    public void start() {
        if (stopped) throw new IllegalStateException("NodePermissionIndex 已停止");
        refreshNodes();
        for (Node n : nodes) {
            refreshAppsAsync(n.getId());
        }
    }

    public void stop() {
        stopped = true;
        refresher.shutdownNow();
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    public synchronized void refreshNodes() {
        nodes = nodeRepository.findAll();
        java.util.Set<String> currentNodeIds = new java.util.HashSet<>();
        for (Node node : nodes) {
            currentNodeIds.add(node.getId());
        }
        synchronizedNodes.retainAll(currentNodeIds);
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<AppRef> appsOf(String nodeId) {
        return nodeApps.getOrDefault(nodeId, List.of());
    }


    public boolean containsAppId(String appId, String excludeNodeId) {
        if (appId == null || appId.isBlank()) {
            return false;
        }
        for (java.util.Map.Entry<String, List<AppRef>> e : nodeApps.entrySet()) {
            if (excludeNodeId != null && excludeNodeId.equals(e.getKey())) {
                continue;
            }
            for (AppRef a : e.getValue()) {
                if (appId.equals(a.id())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean reserveAppId(String appId, String nodeId) {
        requireNodeSynchronized(nodeId);
        return appIdRegistry.reserve(appId, nodeId);
    }

    public boolean activateAppId(String appId, String nodeId) {
        return appIdRegistry.activate(appId, nodeId);
    }

    public void releaseReservation(String appId, String nodeId) {
        appIdRegistry.releaseReservation(appId, nodeId);
    }

    public void releaseOwnedAppId(String appId, String nodeId) {
        appIdRegistry.releaseOwned(appId, nodeId);
    }

    public boolean ownsApp(String nodeId, String appId) {
        return synchronizedNodes.contains(nodeId) && appIdRegistry.owns(appId, nodeId);
    }

    public void requireOwnedApp(String nodeId, String appId) {
        requireNodeSynchronized(nodeId);
        if (!ownsApp(nodeId, appId)) {
            throw ApiException.badRequest("应用 ID 未注册到该节点或存在全局冲突: " + appId);
        }
    }

    private void requireNodeSynchronized(String nodeId) {
        if (!synchronizedNodes.contains(nodeId)) {
            throw ApiException.badRequest("目标节点的应用归属尚未完成同步, 已拒绝应用操作");
        }
    }

    public static void requireCurrentOwnership(String nodeId, String appId) {
        NodePermissionIndex index = activeInstance;
        if (index == null) {
            throw ApiException.badRequest("应用归属索引尚未就绪");
        }
        index.requireOwnedApp(nodeId, appId);
    }

    public void refreshAppsAsync(String nodeId) {
        if (stopped) return;
        refresher.execute(() -> refreshAppsSync(nodeId));
    }

    private void refreshAppsSync(String nodeId) {
        try {
            DaemonConnection conn = nodeService.getConnection(nodeId);
            if (conn == null) {
                return;
            }
            Object resp = conn.requestBlocking("app:list", Map.of(), 4000);
            if (resp instanceof JsonNode n && n.path("status").asInt(-1) == 200) {
                synchronizeSnapshot(nodeId, n.path("data"));
            }
        } catch (Exception e) {
            log.debug("刷新节点 [{}] 应用列表失败: {}", nodeId, e.getMessage());
        }
    }

    public synchronized void synchronizeSnapshot(String nodeId, JsonNode apps) {
        List<AppRef> list = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        if (apps != null && apps.isArray()) {
            for (JsonNode item : apps) {
                if (item.isObject()) {
                    String id = item.path("id").asText("");
                    if (!id.isBlank()) {
                        ids.add(id);
                        list.add(new AppRef(id, item.path("name").asText("")));
                    }
                }
            }
        }
        appIdRegistry.synchronize(nodeId, ids);
        Map<String, List<AppRef>> copy = new HashMap<>(nodeApps);
        copy.put(nodeId, list);
        nodeApps = copy;
        synchronizedNodes.add(nodeId);
    }
}
