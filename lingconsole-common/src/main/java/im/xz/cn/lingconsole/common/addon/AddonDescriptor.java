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

import java.util.List;


public record AddonDescriptor(
        String name,
        String version,
        String mainClass,
        String author,
        String description,
        String apiVersion,
        List<String> dependencies,
        List<String> softDependencies) {

    public List<String> dependencies() {
        return dependencies == null ? List.of() : dependencies;
    }

    public List<String> softDependencies() {
        return softDependencies == null ? List.of() : softDependencies;
    }

    public im.xz.cn.lingconsole.addon.AddonInfo toInfo() {
        return new im.xz.cn.lingconsole.addon.AddonInfo(
                name, version, mainClass, author, description, apiVersion, dependencies());
    }
}
