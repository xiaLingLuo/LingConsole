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
package im.xz.cn.lingconsole.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemInfo {

    private String osName;
    private String osArch;
    private String osVersion;
    private int cpuCores;
    private double cpuUsage;        
    private long totalMemory;       
    private long usedMemory;        
    private long freeMemory;        
    private long diskTotal;         
    private long diskFree;          
    private long diskUsable;        
    private String javaVersion;
    private String jvmUptime;       
    private List<String> jvmArgs;

    public static SystemInfo collect() {
        SystemInfo info = new SystemInfo();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

        info.osName = System.getProperty("os.name");
        info.osArch = System.getProperty("os.arch");
        info.osVersion = System.getProperty("os.version");
        info.cpuCores = os.getAvailableProcessors();

        Runtime rt = Runtime.getRuntime();
        info.totalMemory = rt.totalMemory();
        info.freeMemory = rt.freeMemory();
        info.usedMemory = rt.totalMemory() - rt.freeMemory();

        File[] roots = File.listRoots();
        if (roots != null && roots.length > 0) {
            File root = roots[0];
            info.diskTotal = root.getTotalSpace();
            info.diskFree = root.getFreeSpace();
            info.diskUsable = root.getUsableSpace();
        }

        info.javaVersion = System.getProperty("java.version");

        RuntimeMXBean rmx = ManagementFactory.getRuntimeMXBean();
        long uptimeMillis = rmx.getUptime();
        long mm = uptimeMillis / 60000;
        long ss = (uptimeMillis % 60000) / 1000;
        info.jvmUptime = String.format("%02d:%02d", mm, ss);
        info.jvmArgs = rmx.getInputArguments();

        info.cpuUsage = -1; 
        return info;
    }

    public String getOsName() {
        return osName;
    }

    public String getOsArch() {
        return osArch;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public int getCpuCores() {
        return cpuCores;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public long getUsedMemory() {
        return usedMemory;
    }

    public long getFreeMemory() {
        return freeMemory;
    }

    public long getDiskTotal() {
        return diskTotal;
    }

    public long getDiskFree() {
        return diskFree;
    }

    public long getDiskUsable() {
        return diskUsable;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getJvmUptime() {
        return jvmUptime;
    }

    public List<String> getJvmArgs() {
        return jvmArgs;
    }
}
