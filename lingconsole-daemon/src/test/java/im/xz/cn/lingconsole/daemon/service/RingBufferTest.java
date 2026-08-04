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
package im.xz.cn.lingconsole.daemon.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingBufferTest {

    @Test
    void retainsOrder() {
        RingBuffer buffer = new RingBuffer(10);
        for (int i = 0; i < 5; i++) {
            buffer.add("line" + i);
        }
        assertEquals(List.of("line0", "line1", "line2", "line3", "line4"), buffer.recent(10));
    }

    @Test
    void wrapsWhenFull() {
        RingBuffer buffer = new RingBuffer(3);
        for (int i = 0; i < 5; i++) {
            buffer.add("line" + i);
        }
        assertEquals(List.of("line2", "line3", "line4"), buffer.recent(10));
    }

    @Test
    void recentReturnsLimitedCount() {
        RingBuffer buffer = new RingBuffer(10);
        for (int i = 0; i < 10; i++) {
            buffer.add("line" + i);
        }
        assertEquals(List.of("line6", "line7", "line8", "line9"), buffer.recent(4));
    }

    @Test
    void clearResets() {
        RingBuffer buffer = new RingBuffer(5);
        buffer.add("a");
        buffer.clear();
        assertEquals(0, buffer.size());
        assertEquals(List.of(), buffer.recent(5));
    }

    @Test
    void emptyBuffer() {
        RingBuffer buffer = new RingBuffer(5);
        assertEquals(0, buffer.size());
        assertEquals(List.of(), buffer.recent(10));
    }
}
