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
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hal;
    private final CentralProcessor cpu;

    
    private long[] prevCpuTicks;
    private volatile double lastCpuUsage;

    
    private long lastRxTotal;
    private long lastTxTotal;
    private long lastSampleNanos;

    public MonitorService() {
        this.systemInfo = new SystemInfo();
        this.hal = systemInfo.getHardware();
        this.cpu = hal.getProcessor();
        
        try {
            prevCpuTicks = cpu.getSystemCpuLoadTicks();
            Thread.sleep(200);
            long[] now = cpu.getSystemCpuLoadTicks();
            lastCpuUsage = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100;
            prevCpuTicks = now;
        } catch (Exception e) {
            log.debug("CPU 采样预热失败", e);
        }
        long[] net = readNetworkTotals();
        lastRxTotal = net[0];
        lastTxTotal = net[1];
        lastSampleNanos = System.nanoTime();
    }

    public double currentCpuUsage() {
        return lastCpuUsage;
    }

    
    public synchronized MonitorSnapshot snapshot() {
        MonitorSnapshot snap = new MonitorSnapshot();

        
        try {
            long[] now = cpu.getSystemCpuLoadTicks();
            if (prevCpuTicks != null) {
                double load = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100;
                lastCpuUsage = Math.clamp(load, 0, 100);
            }
            prevCpuTicks = now;
        } catch (Exception e) {
            log.debug("CPU 采样失败", e);
        }
        snap.setCpuUsage(lastCpuUsage);
        try {
            double[] load = cpu.getSystemLoadAverage(1);
            snap.setLoadAverage(load.length > 0 ? load[0] : -1);
        } catch (Exception e) {
            snap.setLoadAverage(-1);
        }

        
        GlobalMemory memory = hal.getMemory();
        snap.setMemoryTotal(memory.getTotal());
        snap.setMemoryFree(memory.getAvailable());
        snap.setMemoryUsed(memory.getTotal() - memory.getAvailable());

        
        List<MonitorSnapshot.DiskUsage> disks = new ArrayList<>();
        FileSystem fileSystem = systemInfo.getOperatingSystem().getFileSystem();
        for (OSFileStore store : fileSystem.getFileStores()) {
            try {
                MonitorSnapshot.DiskUsage disk = new MonitorSnapshot.DiskUsage();
                disk.setMount(store.getMount());
                disk.setTotal(store.getTotalSpace());
                disk.setFree(store.getFreeSpace());
                disk.setUsable(store.getUsableSpace());
                disks.add(disk);
            } catch (Exception e) {
                log.debug("读取磁盘信息失败: {}", store.getName(), e);
            }
        }
        snap.setDisks(disks);

        
        long[] net = readNetworkTotals();
        snap.setNetworkRxTotal(net[0]);
        snap.setNetworkTxTotal(net[1]);
        long nowNanos = System.nanoTime();
        long elapsed = Math.max(1, nowNanos - lastSampleNanos);
        snap.setNetworkRxRate(Math.max(0, (net[0] - lastRxTotal) * 1_000_000_000.0 / elapsed));
        snap.setNetworkTxRate(Math.max(0, (net[1] - lastTxTotal) * 1_000_000_000.0 / elapsed));
        lastRxTotal = net[0];
        lastTxTotal = net[1];
        lastSampleNanos = nowNanos;

        snap.setTimestamp(System.currentTimeMillis() / 1000);
        return snap;
    }

    
    private long[] readNetworkTotals() {
        long rx = 0;
        long tx = 0;
        try {
            for (NetworkIF nif : hal.getNetworkIFs()) {
                try {
                    nif.updateAttributes();
                } catch (Exception _) {
                    
                }
                if (nif.getIfOperStatus() == NetworkIF.IfOperStatus.UP
                        && !nif.getName().equals("lo")) {
                    rx += nif.getBytesRecv();
                    tx += nif.getBytesSent();
                }
            }
        } catch (Exception e) {
            log.debug("读取网络统计失败", e);
        }
        return new long[]{rx, tx};
    }
}
