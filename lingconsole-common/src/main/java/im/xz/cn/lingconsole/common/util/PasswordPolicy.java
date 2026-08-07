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
package im.xz.cn.lingconsole.common.util;


public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private static final java.util.regex.Pattern HAS_LETTER =
            java.util.regex.Pattern.compile("[A-Za-z]");
    private static final java.util.regex.Pattern HAS_DIGIT =
            java.util.regex.Pattern.compile("[0-9]");

    private PasswordPolicy() {
    }

    public static String validate(String newPassword, String oldPassword, String username) {
        if (newPassword == null || newPassword.length() < MIN_LENGTH) {
            return "密码长度至少 " + MIN_LENGTH + " 位";
        }
        if (!HAS_LETTER.matcher(newPassword).find() || !HAS_DIGIT.matcher(newPassword).find()) {
            return "密码必须同时包含字母和数字";
        }
        if (oldPassword != null && newPassword.equals(oldPassword)) {
            return "新密码不能与原密码相同";
        }
        if (username != null && newPassword.equalsIgnoreCase(username)) {
            return "密码不能与用户名相同";
        }
        return null;
    }
}
