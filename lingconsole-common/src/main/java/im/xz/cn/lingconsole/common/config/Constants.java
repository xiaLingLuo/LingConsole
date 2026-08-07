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
package im.xz.cn.lingconsole.common.config;


public final class Constants {

    
    public static final String DATA_DIR = "/lingConsole";

    
    public static final String WEB_DIR = DATA_DIR + "/web";

    
    public static final String DAMON_DIR = DATA_DIR + "/damon";

    
    public static final String APPS_DIR = DATA_DIR + "/apps";

    
    public static final String WEB_CONFIG_FILE = WEB_DIR + "/config.toml";

    
    public static final String DAMON_CONFIG_FILE = DAMON_DIR + "/config.toml";

    
    public static final int DEFAULT_WEB_PORT = 55600;

    
    public static final int DEFAULT_DAMON_PORT = 55700;

    
    public static final String WEB_API_PREFIX = "/api";

    
    public static final String DAMON_API_PREFIX = "/consoleapi";

    
    public static final String SOCKET_IO_PATH = "/socket.io/";

    
    public static final String PANEL_NS = "/panel";

    
    public static final String DAMON_NS = "/daemon";

    
    public static final String STREAM_NS = "/stream";

    
    public static final String DEFAULT_USERNAME = "ling";

    
    public static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    
    public static final String VERSION = "1.2.8";

    
    public static final String REPOSITORY_URL = "https://github.com/xiaLingLuo/LingConsole";

    
    public static final String REPOSITORY_NAME = "xiaLingLuo/LingConsole";

    private Constants() {
    }
}
