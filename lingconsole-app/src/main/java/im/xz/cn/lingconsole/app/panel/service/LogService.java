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

import im.xz.cn.lingconsole.app.panel.model.OperationLog;
import im.xz.cn.lingconsole.app.panel.repository.LogRepository;
import im.xz.cn.lingconsole.common.util.IdUtil;

import java.util.List;


public class LogService {

    private final LogRepository logRepository;

    public LogService(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public void record(String userId, String action, String target, String detail, String ip) {
        OperationLog log = new OperationLog();
        log.setId(IdUtil.uuid());
        log.setUserId(userId);
        log.setAction(action);
        log.setTarget(target);
        log.setDetail(detail);
        log.setIp(ip);
        log.setCreatedAt(System.currentTimeMillis() / 1000);
        logRepository.insert(log);
    }

    public List<OperationLog> list(int page, int pageSize) {
        return logRepository.findAll(Math.max(1, page), Math.clamp(pageSize, 1, 100));
    }
}
