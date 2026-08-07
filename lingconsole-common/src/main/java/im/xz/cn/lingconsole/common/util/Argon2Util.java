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

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.util.encoders.Hex;
import java.security.SecureRandom;
import java.util.regex.Pattern;


public final class Argon2Util {

    private static final int ARGON_VERSION = 19;
    private static final int MEMORY_KIB = 64 * 1024;   
    private static final int ITERATIONS = 3;
    private static final int PARALLELISM = 4;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int MIN_MEMORY_KIB = 8 * 1024;
    private static final int MAX_MEMORY_KIB = 256 * 1024;
    private static final int MAX_ITERATIONS = 10;
    private static final int MAX_PARALLELISM = 16;
    private static final int MIN_SALT_LENGTH = 8;
    private static final int MAX_SALT_LENGTH = 64;
    private static final int MIN_HASH_LENGTH = 16;
    private static final int MAX_HASH_LENGTH = 64;

    private static final Pattern PATTERN =
            Pattern.compile("^argon2id\\$v=19\\$m=\\d+,t=\\d+,p=\\d+\\$([0-9a-f]+)\\$([0-9a-f]+)$");

    private Argon2Util() {
    }

    public static String hash(String password) {
        return hash(password, generateSalt(), MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_LENGTH);
    }

    public static String hash(String password, byte[] salt, int memoryKib, int iterations, int parallelism, int hashLength) {
        if (password == null || !parametersAllowed(salt, memoryKib, iterations, parallelism, hashLength)) {
            throw new IllegalArgumentException("Argon2 参数超出安全范围");
        }
        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(ARGON_VERSION)
                .withMemoryAsKB(memoryKib)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt);
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());
        byte[] hash = new byte[hashLength];
        generator.generateBytes(password.toCharArray(), hash);
        return "argon2id$v=19$m=" + memoryKib + ",t=" + iterations + ",p=" + parallelism
                + "$" + Hex.toHexString(salt) + "$" + Hex.toHexString(hash);
    }

    public static boolean verify(String password, String encoded) {
        if (encoded == null || password == null) {
            return false;
        }
        java.util.regex.Matcher m = PATTERN.matcher(encoded);
        if (!m.matches()) {
            return false;
        }
        if (m.group(1).length() < MIN_SALT_LENGTH * 2 || m.group(1).length() > MAX_SALT_LENGTH * 2
                || m.group(2).length() < MIN_HASH_LENGTH * 2 || m.group(2).length() > MAX_HASH_LENGTH * 2) {
            return false;
        }
        byte[] salt;
        byte[] expected;
        int[] params;
        try {
            salt = Hex.decode(m.group(1));
            expected = Hex.decode(m.group(2));
            params = parseParams(encoded);
        } catch (RuntimeException e) {
            return false;
        }
        if (!parametersAllowed(salt, params[0], params[1], params[2], expected.length)) {
            return false;
        }
        byte[] actual = new byte[expected.length];
        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(ARGON_VERSION)
                .withMemoryAsKB(params[0])
                .withIterations(params[1])
                .withParallelism(params[2])
                .withSalt(salt);
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());
        generator.generateBytes(password.toCharArray(), actual);
        return MessageDigest_isEqual(expected, actual);
    }

    private static boolean MessageDigest_isEqual(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        return java.security.MessageDigest.isEqual(a, b);
    }

    private static int[] parseParams(String encoded) {
        
        String[] parts = encoded.split("\\$");
        String params = parts[2];
        String[] kv = params.split(",");
        int m = Integer.parseInt(kv[0].substring(2));
        int t = Integer.parseInt(kv[1].substring(2));
        int p = Integer.parseInt(kv[2].substring(2));
        return new int[]{m, t, p};
    }

    private static boolean parametersAllowed(byte[] salt, int memoryKib, int iterations,
                                             int parallelism, int hashLength) {
        return salt != null && salt.length >= MIN_SALT_LENGTH && salt.length <= MAX_SALT_LENGTH
                && memoryKib >= MIN_MEMORY_KIB && memoryKib <= MAX_MEMORY_KIB
                && iterations >= 1 && iterations <= MAX_ITERATIONS
                && parallelism >= 1 && parallelism <= MAX_PARALLELISM
                && hashLength >= MIN_HASH_LENGTH && hashLength <= MAX_HASH_LENGTH;
    }

    private static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }
}
