/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package im.xz.cn.lingconsole.app.panel.service;

import im.xz.cn.lingconsole.app.panel.model.OperationLog;
import im.xz.cn.lingconsole.app.panel.repository.LogRepository;
import im.xz.cn.lingconsole.common.util.IdUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 500;
    static final int PLUGIN_LOGS_PER_MINUTE = 120;

    public record Page(List<OperationLog> logs, long total, int page, int pageSize, int totalPages,
                       LogRepository.Query filters) {
    }

    private static final class PluginWindow {
        private long minute;
        private int accepted;
        private int suppressed;
        private String suppressionLogId;
    }

    private final LogRepository logRepository;
    private final Map<String, PluginWindow> pluginWindows = new HashMap<>();

    public LogService(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public void record(String userId, String action, String target, String detail, String ip) {
        recordCore(userId, action, target, detail, ip, null, null, null);
    }

    public void recordCore(String userId, String action, String target, String detail, String ip,
                           String nodeId, String appId, String requestId) {
        insert("CORE", null, userId == null ? "SYSTEM" : "USER", userId, action, target,
                detail, ip, nodeId, appId, requestId);
    }

    public void recordSystem(String action, String target, String detail, String nodeId,
                             String appId, String requestId) {
        insert("CORE", null, "SYSTEM", null, action, target, detail, null, nodeId, appId, requestId);
    }

    public synchronized void recordPlugin(String pluginName, String action, String target, String detail,
                                          String nodeId, String appId, String requestId) {
        if (pluginName == null || pluginName.isBlank()) {
            throw new IllegalArgumentException("插件名不能为空");
        }
        long minute = System.currentTimeMillis() / 60_000;
        PluginWindow window = pluginWindows.computeIfAbsent(pluginName, ignored -> new PluginWindow());
        if (window.minute != minute) {
            window.minute = minute;
            window.accepted = 0;
            window.suppressed = 0;
            window.suppressionLogId = null;
        }
        if (window.accepted < PLUGIN_LOGS_PER_MINUTE) {
            window.accepted++;
            insert("PLUGIN", pluginName, "SYSTEM", null, action, target, detail,
                    "addon", nodeId, appId, requestId);
            return;
        }
        window.suppressed++;
        String suppressionDetail = "插件日志超过每分钟软上限，已抑制 " + window.suppressed + " 条";
        if (window.suppressionLogId == null) {
            window.suppressionLogId = IdUtil.uuid();
            OperationLog log = create(window.suppressionLogId, "PLUGIN", pluginName, "SYSTEM", null,
                    "log.suppressed", pluginName, suppressionDetail, "addon", null, null, requestId);
            logRepository.insert(log);
        } else {
            logRepository.updateDetail(window.suppressionLogId, suppressionDetail);
        }
    }

    public Page list(LogRepository.Query requested, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        LogRepository.Query query = normalize(requested);
        long total = logRepository.count(query);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safePageSize);
        return new Page(logRepository.find(query, safePage, safePageSize), total,
                safePage, safePageSize, totalPages, query);
    }

    public List<OperationLog> list(int page, int pageSize) {
        return list(null, page, pageSize).logs();
    }

    public List<OperationLog> findByUser(String userId, int limit) {
        return logRepository.findByUserId(userId, Math.clamp(limit, 1, 100));
    }

    private LogRepository.Query normalize(LogRepository.Query query) {
        if (query == null) {
            return new LogRepository.Query(null, "CORE", null, null, null, null, null, null, null, null);
        }
        String source = query.sourceType() == null ? "CORE" : query.sourceType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!source.equals("CORE") && !source.equals("PLUGIN") && !source.equals("ALL")) {
            source = "CORE";
        }
        String q = trim(query.q());
        if (q != null && q.length() > 200) {
            q = q.substring(0, 200);
        }
        return new LogRepository.Query(q, source, trim(query.pluginName()), trim(query.userId()),
                trim(query.nodeId()), trim(query.appId()), trim(query.action()), trim(query.requestId()),
                query.startTime(), query.endTime());
    }

    private void insert(String sourceType, String pluginName, String actorType, String userId,
                        String action, String target, String detail, String ip, String nodeId,
                        String appId, String requestId) {
        logRepository.insert(create(IdUtil.uuid(), sourceType, pluginName, actorType, userId,
                action, target, detail, ip, nodeId, appId, requestId));
    }

    private static OperationLog create(String id, String sourceType, String pluginName, String actorType,
                                       String userId, String action, String target, String detail, String ip,
                                       String nodeId, String appId, String requestId) {
        OperationLog log = new OperationLog();
        log.setId(id);
        log.setSourceType(sourceType);
        log.setPluginName(pluginName);
        log.setActorType(actorType);
        log.setUserId(userId);
        log.setNodeId(nodeId);
        log.setAppId(appId);
        log.setRequestId(requestId);
        log.setAction(action);
        log.setTarget(target);
        log.setDetail(detail);
        log.setIp(ip);
        log.setCreatedAt(System.currentTimeMillis() / 1000);
        return log;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
