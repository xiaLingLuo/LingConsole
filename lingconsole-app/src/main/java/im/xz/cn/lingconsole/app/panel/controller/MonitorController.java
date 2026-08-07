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
package im.xz.cn.lingconsole.app.panel.controller;

import com.fasterxml.jackson.databind.JsonNode;
import im.xz.cn.lingconsole.app.panel.exception.ApiException;
import im.xz.cn.lingconsole.app.panel.middleware.PermissionMiddleware;
import im.xz.cn.lingconsole.app.panel.remote.DaemonConnection;
import im.xz.cn.lingconsole.app.panel.service.NodeService;
import im.xz.cn.lingconsole.common.util.ErrorMessageUtil;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.Map;


public class MonitorController {

    private final NodeService nodeService;

    public MonitorController(NodeService nodeService) {
        this.nodeService = nodeService;
    }
    public void register(RoutesConfig routes, String prefix) {
        routes.get(prefix + "/nodes/{nodeId}/monitor", this::monitor);
    }

    private void monitor(Context ctx) {
        String nodeId = ctx.pathParam("nodeId");
        PermissionMiddleware.requirePermission(ctx, "lingconsole.monitor.read." + nodeId);
        DaemonConnection conn = nodeService.getConnection(nodeId);
        if (conn == null) {
            throw ApiException.badRequest("节点离线或未连接, 请确保 55700 端口可被访问");
        }
        try {
            Object resp = conn.requestBlocking("monitor:stats", Map.of(), 5000);
            if (resp instanceof JsonNode n) {
                int status = n.path("status").asInt(-1);
                if (status != 200) {
                    throw ApiException.badRequest(n.path("message").asText("获取监控失败"));
                }
                ctx.json(im.xz.cn.lingconsole.common.model.ApiResponse.ok(n.path("data")));
                return;
            }
            throw ApiException.badRequest("节点返回无效响应");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorMessageUtil.with("获取监控失败", e));
        }
    }
}
