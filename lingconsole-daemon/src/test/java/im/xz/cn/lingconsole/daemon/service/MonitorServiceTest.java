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

import im.xz.cn.lingconsole.daemon.model.MonitorSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorServiceTest {

    @Test
    void snapshotReturnsSaneValues() {
        MonitorService service = new MonitorService();
        MonitorSnapshot snap = service.snapshot();

        assertTrue(snap.getCpuUsage() >= 0, "CPU 使用率应 >= 0");
        assertTrue(snap.getCpuUsage() <= 100, "CPU 使用率应 <= 100");
        assertTrue(snap.getMemoryTotal() > 0, "总内存应 > 0");
        assertTrue(snap.getMemoryUsed() >= 0);
        assertTrue(snap.getMemoryFree() >= 0);
        assertNotNull(snap.getDisks());
        assertTrue(snap.getTimestamp() > 0);
    }

    @Test
    void repeatedSnapshotsStayStable() {
        MonitorService service = new MonitorService();
        service.snapshot();
        MonitorSnapshot second = service.snapshot();
        assertTrue(second.getMemoryTotal() > 0);
        assertTrue(second.getNetworkRxTotal() >= 0);
        assertTrue(second.getNetworkTxTotal() >= 0);
    }
}
