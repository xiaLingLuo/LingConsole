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
package im.xz.cn.lingconsole.common.permission;

import java.util.Set;


public final class Permissions {

    private Permissions() {
    }

    public static final String USER_MANAGE = "lingconsole.user.manage";
    public static final String NODE_READ = "lingconsole.node.read";
    public static final String NODE_WRITE = "lingconsole.node.write";
    public static final String APP_READ = "lingconsole.app.read";
    public static final String APP_WRITE = "lingconsole.app.write";
    public static final String APP_ADVANCED = "lingconsole.app.advanced";
    public static final String FILE_NODE = "lingconsole.file.node";
    public static final String FILE_APP = "lingconsole.file.app";
    public static final String TERMINAL_NODE = "lingconsole.terminal.node";
    public static final String TERMINAL_APP = "lingconsole.terminal.app";
    public static final String MONITOR_READ = "lingconsole.monitor.read";
    public static final String SYSTEM_STATUS = "lingconsole.system.status";
    public static final String LOG_READ = "lingconsole.log.read";
    public static final String PACKAGES = "lingconsole.packages.manage";
    public static final String USER_BANNED = "lingconsole.user.banned";

    public static final String PERMISSION_ASSIGN = "lingconsole.permission.assign";
    public static final String DASHBOARD_ADMIN = "lingconsole.dashboard.admin";
    public static final String DASHBOARD_USER = "lingconsole.dashboard.user";

    
    public static final Set<String> ROOT_ALL = Set.of("*");

    
    public static final Set<String> ALL = Set.of(
            USER_MANAGE, NODE_READ, NODE_WRITE, APP_READ, APP_WRITE, APP_ADVANCED,
            FILE_NODE, FILE_APP, TERMINAL_NODE, TERMINAL_APP, MONITOR_READ,
            SYSTEM_STATUS, LOG_READ, PACKAGES, USER_BANNED, PERMISSION_ASSIGN,
            DASHBOARD_ADMIN, DASHBOARD_USER,
            "lingconsole.node.read.*", "lingconsole.node.write.*",
            "lingconsole.app.read.*", "lingconsole.app.write.*", "lingconsole.app.advanced.*",
            "lingconsole.file.node.*", "lingconsole.file.app.*",
            "lingconsole.terminal.node.*", "lingconsole.terminal.app.*",
            "lingconsole.monitor.read.*");

    
    public static final Set<String> GRANTABLE = Set.of(
            SYSTEM_STATUS, LOG_READ, PACKAGES, USER_BANNED,
            DASHBOARD_ADMIN, DASHBOARD_USER,
            USER_MANAGE, PERMISSION_ASSIGN,
            "lingconsole.node.write.*", "lingconsole.app.read.*", "lingconsole.app.write.*",
            "lingconsole.app.advanced.*", "lingconsole.file.node.*", "lingconsole.file.app.*",
            "lingconsole.terminal.node.*", "lingconsole.terminal.app.*",
            "lingconsole.monitor.read.*");
}
