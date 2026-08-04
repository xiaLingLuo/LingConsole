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
package im.xz.cn.lingconsole.daemon.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class MonitorSnapshot {

    
    public static class DiskUsage {
        private String mount;
        private long total;
        private long free;
        private long usable;

        public String getMount() {
            return mount;
        }

        public void setMount(String mount) {
            this.mount = mount;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public long getFree() {
            return free;
        }

        public void setFree(long free) {
            this.free = free;
        }

        public long getUsable() {
            return usable;
        }

        public void setUsable(long usable) {
            this.usable = usable;
        }
    }

    private double cpuUsage;          
    private double loadAverage;       
    private long memoryTotal;         
    private long memoryUsed;          
    private long memoryFree;          
    private double networkRxRate;     
    private double networkTxRate;     
    private long networkRxTotal;      
    private long networkTxTotal;      
    private List<DiskUsage> disks;
    private long timestamp;

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getLoadAverage() {
        return loadAverage;
    }

    public void setLoadAverage(double loadAverage) {
        this.loadAverage = loadAverage;
    }

    public long getMemoryTotal() {
        return memoryTotal;
    }

    public void setMemoryTotal(long memoryTotal) {
        this.memoryTotal = memoryTotal;
    }

    public long getMemoryUsed() {
        return memoryUsed;
    }

    public void setMemoryUsed(long memoryUsed) {
        this.memoryUsed = memoryUsed;
    }

    public long getMemoryFree() {
        return memoryFree;
    }

    public void setMemoryFree(long memoryFree) {
        this.memoryFree = memoryFree;
    }

    public double getNetworkRxRate() {
        return networkRxRate;
    }

    public void setNetworkRxRate(double networkRxRate) {
        this.networkRxRate = networkRxRate;
    }

    public double getNetworkTxRate() {
        return networkTxRate;
    }

    public void setNetworkTxRate(double networkTxRate) {
        this.networkTxRate = networkTxRate;
    }

    public long getNetworkRxTotal() {
        return networkRxTotal;
    }

    public void setNetworkRxTotal(long networkRxTotal) {
        this.networkRxTotal = networkRxTotal;
    }

    public long getNetworkTxTotal() {
        return networkTxTotal;
    }

    public void setNetworkTxTotal(long networkTxTotal) {
        this.networkTxTotal = networkTxTotal;
    }

    public List<DiskUsage> getDisks() {
        return disks;
    }

    public void setDisks(List<DiskUsage> disks) {
        this.disks = disks;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
