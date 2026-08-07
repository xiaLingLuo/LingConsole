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
package im.xz.cn.lingconsole.common.addon;

import java.util.Set;

/**
 * 插件反向代理配置。
 * 面板 /api/addon/&lt;addonName&gt;&lt;mountPath&gt;/* 转发到后端
 * requiredPermission: null 表示默认 (permission.assign), "*" 表示任意已登录用户。
 * forwardHeaders: 显式声明允许转发到后端的请求头。默认仅转发安全头, Cookie/Authorization 等敏感头需显式声明
 */
public record AddonProxy(
        String addonName,
        String mountPath,
        String scheme,
        String host,
        int port,
        String basePath,
        String requiredPermission,
        Set<String> forwardHeaders) {
}
