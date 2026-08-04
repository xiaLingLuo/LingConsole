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
package im.xz.cn.lingconsole.addon.service;

import im.xz.cn.lingconsole.addon.ConfigEntry;
import im.xz.cn.lingconsole.addon.ConfigType;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;


public interface ConfigService {

    
    void define(String key, ConfigType type, String label, String description, String defaultValue);

    
    void defineSelect(String key, String label, String description, String defaultValue, List<String> options);

    
    String getString(String key, String def);

    
    int getInt(String key, int def);

    
    boolean getBoolean(String key, boolean def);

    
    List<ConfigEntry> entries();

    
    Map<String, String> values();

    
    Map<String, Object> panelConfig();

    
    Map<String, Object> daemonConfig();

    
    Path dataDir();
}
