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
import java.util.Map;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppInfo {

    private String id;
    private String name;
    private String type;
    private int status;
    private Long pid;
    private String command;
    private String workDir;
    private String runAsUser;
    private boolean autoStart;
    private boolean autoRestart;
    private int maxRestartCount;
    private int restartCount;
    private long startedAt;
    private List<String> recentLog;
    private List<String> args;
    private String encoding;
    private String ptyType;
    private Map<String, String> environment;
    private boolean protectAppFilesFromSymlinkEscape;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Long getPid() {
        return pid;
    }

    public void setPid(Long pid) {
        this.pid = pid;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public String getRunAsUser() {
        return runAsUser;
    }

    public void setRunAsUser(String runAsUser) {
        this.runAsUser = runAsUser;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isAutoRestart() {
        return autoRestart;
    }

    public void setAutoRestart(boolean autoRestart) {
        this.autoRestart = autoRestart;
    }

    public int getRestartCount() {
        return restartCount;
    }

    public void setRestartCount(int restartCount) {
        this.restartCount = restartCount;
    }

    public int getMaxRestartCount() {
        return maxRestartCount;
    }

    public void setMaxRestartCount(int maxRestartCount) {
        this.maxRestartCount = maxRestartCount;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getPtyType() {
        return ptyType;
    }

    public void setPtyType(String ptyType) {
        this.ptyType = ptyType;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public List<String> getRecentLog() {
        return recentLog;
    }

    public void setRecentLog(List<String> recentLog) {
        this.recentLog = recentLog;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    public boolean isProtectAppFilesFromSymlinkEscape() {
        return protectAppFilesFromSymlinkEscape;
    }

    public void setProtectAppFilesFromSymlinkEscape(boolean protectAppFilesFromSymlinkEscape) {
        this.protectAppFilesFromSymlinkEscape = protectAppFilesFromSymlinkEscape;
    }
}
