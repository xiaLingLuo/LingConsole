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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void normalPathResolvesUnderBase() {
        Path result = PathUtil.sanitize(tempDir.toString(), "sub/file.txt");
        assertEquals(tempDir.resolve("sub/file.txt").normalize(), result);
    }

    @Test
    void rejectsTraversal() {
        assertThrows(SecurityException.class, () ->
                PathUtil.sanitize(tempDir.toString(), "../secret.txt"));
        assertThrows(SecurityException.class, () ->
                PathUtil.sanitize(tempDir.toString(), "a/../../secret.txt"));
    }

    @Test
    void encodedTraversalIsLiteralFilename() {
        
        Path result = PathUtil.sanitize(tempDir.toString(), "..%2fsecret");
        assertEquals(tempDir.resolve("..%2fsecret").normalize(), result);
    }

    @Test
    void decodedTraversalRejected() {
        
        assertThrows(SecurityException.class, () ->
                PathUtil.sanitize(tempDir.toString(), java.net.URLDecoder.decode("..%2f..%2fetc", java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void isUnderWorks() {
        assertTrue(PathUtil.isUnder(tempDir.toString(), tempDir.resolve("a/b").toString()));
        assertFalse(PathUtil.isUnder(tempDir.toString(), tempDir.getParent().resolve("other").toString()));
    }

    @Test
    void absolutePathInsideBase() {
        Path abs = tempDir.resolve("x").toAbsolutePath();
        Path result = PathUtil.sanitize(tempDir.toString(), abs.toString());
        assertEquals(abs.normalize(), result);
    }

    @Test
    void safeFileNameStripsDangerousChars() {
        assertEquals("file", PathUtil.safeFileName(null));
        assertEquals("report.pdf", PathUtil.safeFileName(Path.of("/data", "report.pdf")));
        assertEquals("a;b.txt", PathUtil.safeFileName(Path.of("/data", "a;b.txt")));
    }
}
