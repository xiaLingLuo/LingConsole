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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Argon2UtilTest {

    @Test
    void hashAndVerifyRoundTrip() {
        String hash = Argon2Util.hash("test-password-123");
        assertTrue(hash.startsWith("argon2id$v=19$"));
        assertTrue(Argon2Util.verify("test-password-123", hash));
    }

    @Test
    void verifyRejectsWrongPassword() {
        String hash = Argon2Util.hash("correct-password");
        assertFalse(Argon2Util.verify("wrong-password", hash));
    }

    @Test
    void verifyRejectsMalformed() {
        assertFalse(Argon2Util.verify("any", null));
        assertFalse(Argon2Util.verify("any", "not-a-hash"));
        assertFalse(Argon2Util.verify("any", "argon2id$v=19$bad"));
        assertFalse(Argon2Util.verify("any",
                "argon2id$v=19$m=2147483647,t=3,p=4$00112233445566778899aabbccddeeff$"
                        + "0000000000000000000000000000000000000000000000000000000000000000"));
        assertFalse(Argon2Util.verify("any",
                "argon2id$v=19$m=65536,t=999,p=4$00112233445566778899aabbccddeeff$"
                        + "0000000000000000000000000000000000000000000000000000000000000000"));
        assertFalse(Argon2Util.verify("any",
                "argon2id$v=19$m=65536,t=3,p=4$" + "00".repeat(65) + "$" + "00".repeat(32)));
    }

    @Test
    void hashIsRandomizedBySalt() {
        assertNotEquals(Argon2Util.hash("same-password"), Argon2Util.hash("same-password"));
    }

    @Test
    void randomPasswordLengthAndCharset() {
        String pw = IdUtil.randomPassword();
        assertTrue(pw.matches("[A-Za-z0-9]{16}"));
        assertNotEquals(IdUtil.randomPassword(), pw);
    }
}
