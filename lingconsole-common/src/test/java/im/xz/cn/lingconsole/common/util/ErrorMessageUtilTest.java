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

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorMessageUtilTest {

    @Test
    void nullExceptionReturnsGeneric() {
        assertEquals(ErrorMessageUtil.GENERIC, ErrorMessageUtil.friendly(null));
    }

    @Test
    void nullMessageNeverLeaks() {
        String msg = ErrorMessageUtil.friendly(new RuntimeException());
        assertFalse(msg.contains("null"));
        assertFalse(msg.isBlank());
    }

    @Test
    void npeGetsFriendlyText() {
        String msg = ErrorMessageUtil.friendly(new NullPointerException());
        assertFalse(msg.contains("null"));
        assertEquals("服务器内部错误 (空指针异常)", msg);
    }

    @Test
    void connectExceptionGetsFriendlyText() {
        String msg = ErrorMessageUtil.friendly(new ConnectException("Connection refused"));
        assertTrue(msg.contains("无法连接到目标节点"));
    }

    @Test
    void timeoutExceptionGetsFriendlyText() {
        String msg = ErrorMessageUtil.friendly(new TimeoutException());
        assertTrue(msg.contains("超时"));
    }

    @Test
    void descriptiveMessagePassesThrough() {
        String msg = ErrorMessageUtil.friendly(new IllegalStateException("节点离线"));
        assertEquals("节点离线", msg);
    }

    @Test
    void deepestCauseMessageUsed() {
        Throwable root = new IllegalStateException("数据库磁盘已满");
        Throwable wrapped = new RuntimeException("查询失败", root);
        String msg = ErrorMessageUtil.friendly(wrapped);
        assertEquals("数据库磁盘已满", msg);
    }

    @Test
    void withPrefixNeverLeaksNull() {
        String msg = ErrorMessageUtil.with("上传失败", new RuntimeException());
        assertTrue(msg.startsWith("上传失败: "));
        assertFalse(msg.contains("null"));
    }

    @Test
    void windowsAbsolutePathMasked() {
        assertEquals("无法读取目录: <路径> (<路径>)",
                ErrorMessageUtil.maskPaths("无法读取目录: C:\\Documents and Settings (C:\\Documents and Settings)"));
    }

    @Test
    void unixAbsolutePathMasked() {
        assertEquals("不是目录: <路径>",
                ErrorMessageUtil.maskPaths("不是目录: /home/admin/data"));
    }

    @Test
    void urlsAndPlainMessagesUntouched() {
        assertEquals("节点 URL 必须以 ws:// 或 wss:// 开头",
                ErrorMessageUtil.maskPaths("节点 URL 必须以 ws:// 或 wss:// 开头"));
        assertEquals("节点离线", ErrorMessageUtil.maskPaths("节点离线"));
        assertEquals("连接超时, 请检查目标节点是否可达",
                ErrorMessageUtil.maskPaths("连接超时, 请检查目标节点是否可达"));
    }
}
