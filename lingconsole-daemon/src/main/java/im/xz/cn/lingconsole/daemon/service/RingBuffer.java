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

import java.util.ArrayList;
import java.util.List;


public class RingBuffer {

    private final int capacity;
    private final String[] buffer;
    private int head;
    private int size;

    public RingBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.buffer = new String[this.capacity];
    }

    public synchronized void add(String line) {
        buffer[(head + size) % capacity] = line;
        if (size < capacity) {
            size++;
        } else {
            head = (head + 1) % capacity;
        }
    }

    public synchronized List<String> recent(int count) {
        int take = Math.clamp(count, 0, size);
        List<String> result = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            result.add(buffer[(head + size - take + i) % capacity]);
        }
        return result;
    }

    public synchronized void clear() {
        head = 0;
        size = 0;
    }

    public synchronized int size() {
        return size;
    }
}
