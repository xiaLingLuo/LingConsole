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
package im.xz.cn.lingconsole.daemon.model;


public enum AppStatus {

    STOP(0),
    STOPPING(1),
    STARTING(2),
    RUNNING(3);

    private final int value;

    AppStatus(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static AppStatus fromValue(int value) {
        for (AppStatus s : values()) {
            if (s.value == value) {
                return s;
            }
        }
        return STOP;
    }
}
