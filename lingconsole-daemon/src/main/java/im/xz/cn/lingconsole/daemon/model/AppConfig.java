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

import im.xz.cn.lingconsole.common.config.TomlConfig;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class AppConfig extends TomlConfig {

    private String id;
    private String name;
    private String type = "general";
    private boolean autoStart;
    private boolean autoRestart;
    private int maxRestartCount = 3;
    private String command;
    private String workDir;
    private String runAsUser = "";
    private List<String> args = new ArrayList<>();
    private Map<String, String> environment = new LinkedHashMap<>();
    private String encoding = "UTF-8";
    private String ptyType = "xterm-256color";

    private AppConfig(TomlParseResult r) {
        super(r);
    }

    
    public AppConfig() {
        this(Toml.parse(""));
    }

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

    public int getMaxRestartCount() {
        return maxRestartCount;
    }

    public void setMaxRestartCount(int maxRestartCount) {
        this.maxRestartCount = maxRestartCount;
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
        this.runAsUser = runAsUser == null ? "" : runAsUser;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
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

    public static AppConfig load(Path path) throws IOException {
        AppConfig cfg = new AppConfig(TomlConfig.parse(path));
        cfg.id = cfg.str("app.id", null);
        cfg.name = cfg.str("app.name", null);
        cfg.type = cfg.str("app.type", "general");
        cfg.autoStart = cfg.bool("app.autoStart", false);
        cfg.autoRestart = cfg.bool("app.autoRestart", false);
        cfg.maxRestartCount = cfg.intVal("app.maxRestartCount", 3);
        cfg.command = cfg.str("process.command", "");
        cfg.workDir = cfg.str("process.workDir", "");
        cfg.runAsUser = cfg.str("process.runAsUser", "");
        cfg.args = cfg.strList("process.args", new ArrayList<>());
        cfg.environment = cfg.strMap("process.environment");
        cfg.encoding = cfg.str("terminal.encoding", "UTF-8");
        cfg.ptyType = cfg.str("terminal.ptyType", "xterm-256color");
        return cfg;
    }

    
    public String toToml() {
        StringBuilder sb = new StringBuilder();
        sb.append("# LingConsole App 配置\n");
        sb.append("# 由 LingConsole 管理, 请勿手动修改此文件\n\n");

        sb.append("[app]\n");
        sb.append("id = \"").append(escape(id)).append("\"\n");
        sb.append("name = \"").append(escape(name)).append("\"\n");
        sb.append("type = \"").append(escape(type)).append("\"\n");
        sb.append("autoStart = ").append(autoStart).append("\n");
        sb.append("autoRestart = ").append(autoRestart).append("\n");
        sb.append("maxRestartCount = ").append(maxRestartCount).append("\n\n");

        sb.append("[process]\n");
        sb.append("command = \"").append(escape(command == null ? "" : command)).append("\"\n");
        sb.append("workDir = \"").append(escape(workDir == null ? "" : workDir)).append("\"\n");
        sb.append("runAsUser = \"").append(escape(runAsUser == null ? "" : runAsUser)).append("\"\n");
        if (args != null && !args.isEmpty()) {
            sb.append("args = [");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("\"").append(escape(args.get(i))).append("\"");
            }
            sb.append("]\n");
        }
        if (environment != null && !environment.isEmpty()) {
            sb.append("environment = { ");
            boolean first = true;
            for (Map.Entry<String, String> e : environment.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append("\"").append(escape(e.getKey())).append("\" = \"").append(escape(e.getValue())).append("\"");
                first = false;
            }
            sb.append(" }\n");
        }
        sb.append("\n[terminal]\n");
        sb.append("encoding = \"").append(escape(encoding)).append("\"\n");
        sb.append("ptyType = \"").append(escape(ptyType)).append("\"\n");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
