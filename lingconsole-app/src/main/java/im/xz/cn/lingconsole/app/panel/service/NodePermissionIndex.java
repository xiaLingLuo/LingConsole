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

    private volatile List<Node> nodes = List.of();
    private volatile Map<String, List<AppRef>> nodeApps = Map.of();

    private final ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "node-perm-index");
        t.setDaemon(true);
        return t;
    });

    public NodePermissionIndex(NodeRepository nodeRepository, NodeService nodeService) {
        this.nodeRepository = nodeRepository;
        this.nodeService = nodeService;
    }

    public void start() {
        refreshNodes();
        for (Node n : nodes) {
            refreshAppsAsync(n.getId());
        }
    }

    public void stop() {
        refresher.shutdownNow();
    }

    public synchronized void refreshNodes() {
        nodes = nodeRepository.findAll();
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<AppRef> appsOf(String nodeId) {
        return nodeApps.getOrDefault(nodeId, List.of());
    }

    public void refreshAppsAsync(String nodeId) {
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
                List<AppRef> list = new ArrayList<>();
                for (JsonNode item : n.path("data")) {
                    if (item.isObject()) {
                        list.add(new AppRef(item.path("id").asText(""), item.path("name").asText("")));
                    }
                }
                Map<String, List<AppRef>> copy = new HashMap<>(nodeApps);
                copy.put(nodeId, list);
                nodeApps = copy;
            }
        } catch (Exception e) {
            log.debug("刷新节点 [{}] 应用列表失败: {}", nodeId, e.getMessage());
        }
    }
}
