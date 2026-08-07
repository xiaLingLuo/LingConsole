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

import java.security.SecureRandom;
import java.util.UUID;


public final class IdUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String PASSWORD_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String PASSWORD_LETTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String PASSWORD_DIGITS = "0123456789";

    private IdUtil() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String uuidShort() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    
    public static String randomPassword() {
        return randomPassword(16);
    }

    
    public static String randomPassword(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }
        char[] password = new char[length];
        int offset = 0;
        if (length >= 2) {
            password[offset++] = PASSWORD_LETTERS.charAt(RANDOM.nextInt(PASSWORD_LETTERS.length()));
            password[offset++] = PASSWORD_DIGITS.charAt(RANDOM.nextInt(PASSWORD_DIGITS.length()));
        }
        for (int i = offset; i < length; i++) {
            password[i] = PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length()));
        }
        for (int i = password.length - 1; i > 0; i--) {
            int swap = RANDOM.nextInt(i + 1);
            char value = password[i];
            password[i] = password[swap];
            password[swap] = value;
        }
        return new String(password);
    }

    
    public static String randomKey() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
