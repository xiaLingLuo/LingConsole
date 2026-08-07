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

import im.xz.cn.lingconsole.daemon.model.AppConfig;
import im.xz.cn.lingconsole.daemon.model.AppInfo;
import im.xz.cn.lingconsole.common.util.AtomicFileWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class AppManager {

    private static final Logger log = LoggerFactory.getLogger(AppManager.class);

    private final Path appsRoot;
    private final int outputBufferLines;
    private final boolean softShutdownEnabled;
    private final int softShutdownWaitSeconds;
    private final Map<String, AppProcess> processes = new ConcurrentHashMap<>();
    private final Map<String, Object> appLocks = new ConcurrentHashMap<>();

    public AppManager(String appsDir) {
        this(appsDir, 2000);
    }

    public AppManager(String appsDir, int outputBufferLines) {
        this(appsDir, outputBufferLines, false, 1);
    }

    public AppManager(String appsDir, int outputBufferLines, boolean softShutdownEnabled,
                      int softShutdownWaitSeconds) {
        this.appsRoot = Path.of(appsDir);
        this.outputBufferLines = outputBufferLines;
        this.softShutdownEnabled = softShutdownEnabled;
        this.softShutdownWaitSeconds = softShutdownWaitSeconds;
    }

    public void start() {
        if (!Files.isDirectory(appsRoot)) {
            try {
                Files.createDirectories(appsRoot);
            } catch (IOException e) {
                log.error("创建应用目录失败: {}", appsRoot, e);
            }
        }
        
        for (AppInfo info : list()) {
            if (!info.isAutoStart()) {
                continue;
            }
            AppProcess proc = processes.computeIfAbsent(info.getId(), key -> {
                AppConfig cfg = loadConfig(key);
                return cfg == null ? null : new AppProcess(cfg, this, outputBufferLines);
            });
            if (proc != null && !proc.isRunning()) {
                log.info("应用 [{}] autoStart, 正在启动...", info.getName());
                proc.start();
            }
        }
        log.info("AppManager 已启动, 应用目录: {}", appsRoot);
    }

    public void stop() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<Void>> stops = processes.values().stream()
                    .map(process -> executor.submit((java.util.concurrent.Callable<Void>) () -> {
                        if (softShutdownEnabled) {
                            process.stop(softShutdownWaitSeconds);
                        } else {
                            process.destroyNow();
                        }
                        return null;
                    }))
                    .toList();
            for (java.util.concurrent.Future<Void> stop : stops) {
                try {
                    stop.get(softShutdownWaitSeconds + 5L, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("停止应用进程失败或超时", e);
                }
            }
        }
        processes.clear();
    }

    
    
    

    public List<AppInfo> list() {
        List<AppInfo> result = new ArrayList<>();
        if (!Files.isDirectory(appsRoot)) {
            return result;
        }
        try (var stream = Files.list(appsRoot)) {
            List<Path> dirs = stream.filter(Files::isDirectory).sorted().toList();
            for (Path dir : dirs) {
                Path configFile = dir.resolve("config").resolve("config.toml");
                if (!Files.exists(configFile)) {
                    continue;
                }
                try {
                    AppConfig config = AppConfig.load(configFile);
                    AppInfo info = toInfo(config);
                    result.add(info);
                } catch (Exception e) {
                    log.warn("加载应用配置失败: {}", configFile, e);
                }
            }
        } catch (IOException e) {
            log.error("扫描应用目录失败: {}", appsRoot, e);
        }
        
        result.sort(Comparator.comparingInt((AppInfo a) -> a.getStatus() == 0 ? 1 : 0)
                .thenComparing(AppInfo::getName));
        return result;
    }

    public AppInfo get(String id) {
        AppConfig config = loadConfig(id);
        return config == null ? null : toInfo(config);
    }

    private AppConfig loadConfig(String id) {
        if (!validId(id)) {
            return null;
        }
        Path configFile = appsRoot.resolve(id).resolve("config").resolve("config.toml");
        if (!Files.exists(configFile)) {
            return null;
        }
        try {
            return AppConfig.load(configFile);
        } catch (Exception e) {
            log.warn("加载应用配置失败: {}", configFile, e);
            return null;
        }
    }

    
    private static boolean validId(String id) {
        return id != null && id.matches("[a-z0-9]+");
    }

    private AppInfo toInfo(AppConfig config) {
        AppInfo info = new AppInfo();
        info.setId(config.getId());
        info.setName(config.getName());
        info.setType(config.getType());
        info.setCommand(config.getCommand());
        info.setWorkDir(config.getWorkDir());
        info.setRunAsUser(config.getRunAsUser());
        info.setAutoStart(config.isAutoStart());
        info.setAutoRestart(config.isAutoRestart());
        info.setMaxRestartCount(config.getMaxRestartCount());
        info.setArgs(config.getArgs());
        info.setEncoding(config.getEncoding());
        info.setPtyType(config.getPtyType());
        info.setEnvironment(config.getEnvironment());
        info.setProtectAppFilesFromSymlinkEscape(config.isProtectAppFilesFromSymlinkEscape());

        AppProcess proc = processes.get(config.getId());
        if (proc != null) {
            info.setStatus(proc.status().value());
            info.setPid(proc.pid());
            info.setRestartCount(proc.restartCount());
            info.setStartedAt(proc.startedAt());
            info.setRecentLog(proc.recentLog(50));
        } else {
            info.setStatus(0);
        }
        return info;
    }

    
    
    

    
    public AppInfo create(String id, String name, String command, String type,
                          boolean autoStart, boolean autoRestart, int maxRestartCount,
                          List<String> args, Map<String, String> environment) {
        if (!validId(id)) {
            throw new IllegalArgumentException("应用 ID 仅允许小写英文字母和阿拉伯数字");
        }
        synchronized (lockFor(id)) {
            Path dir = appsRoot.resolve(id);
            boolean reserved = false;
            AppProcess proc = null;
            try {
                Files.createDirectories(appsRoot);
                Files.createDirectory(dir);
                reserved = true;
                Files.createDirectories(dir.resolve("config"));
                Files.createDirectories(dir.resolve("data"));

            AppConfig config = new AppConfig();
            config.setId(id);
            config.setName(name == null || name.isBlank() ? id : name);
            config.setType(type == null || type.isBlank() ? "general" : type);
            config.setAutoStart(autoStart);
            config.setAutoRestart(autoRestart);
            config.setMaxRestartCount(maxRestartCount > 0 ? maxRestartCount : 3);
            config.setCommand(command);
            config.setWorkDir(dir.resolve("data").toAbsolutePath().toString());
            if (args != null) {
                config.setArgs(new ArrayList<>(args));
            }
            if (environment != null) {
                config.setEnvironment(new LinkedHashMap<>(environment));
            }

                AtomicFileWriter.writeString(dir.resolve("config").resolve("config.toml"), config.toToml());
                proc = new AppProcess(config, this, outputBufferLines);
                processes.put(id, proc);
                log.info("应用已创建: {} ({})", config.getName(), id);
                if (config.isAutoStart()) {
                    proc.start();
                }
                return toInfo(config);
            } catch (java.nio.file.FileAlreadyExistsException e) {
                throw new IllegalArgumentException("应用 ID 已存在: " + id);
            } catch (Exception e) {
                if (proc != null) {
                    processes.remove(id, proc);
                    proc.destroyNow();
                }
                if (reserved) {
                    try {
                        deleteRecursively(dir);
                    } catch (IOException cleanupError) {
                        e.addSuppressed(cleanupError);
                    }
                }
                log.error("创建应用失败: {}", name, e);
                throw new IllegalStateException("创建应用失败: " + e.getMessage(), e);
            }
        }
    }

    
    public AppInfo update(String id, String name, String command, String type,
                           boolean autoStart, boolean autoRestart, int maxRestartCount,
                           List<String> args, Map<String, String> environment,
                           String workDir, String encoding, String ptyType,
                           String runAsUser) {
        return update(id, name, command, type, autoStart, autoRestart, maxRestartCount,
                args, environment, workDir, encoding, ptyType, runAsUser, null);
    }

    public AppInfo update(String id, String name, String command, String type,
                           boolean autoStart, boolean autoRestart, int maxRestartCount,
                           List<String> args, Map<String, String> environment,
                           String workDir, String encoding, String ptyType,
                           String runAsUser, Boolean protectAppFilesFromSymlinkEscape) {
        synchronized (lockFor(id)) {
        AppConfig config = loadConfig(id);
        if (config == null) {
            return null;
        }
        if (name != null && !name.isBlank()) {
            config.setName(name);
        }
        config.setAutoStart(autoStart);
        config.setAutoRestart(autoRestart);
        if (protectAppFilesFromSymlinkEscape != null) {
            config.setProtectAppFilesFromSymlinkEscape(protectAppFilesFromSymlinkEscape);
        }

        
        AppProcess proc = processes.get(id);
        boolean running = proc != null && proc.isRunning();
        if (!running) {
            if (command != null) {
                config.setCommand(command);
            }
            if (type != null && !type.isBlank()) {
                config.setType(type);
            }
            if (maxRestartCount > 0) {
                config.setMaxRestartCount(maxRestartCount);
            }
            if (args != null) {
                config.setArgs(new ArrayList<>(args));
            }
            if (environment != null) {
                config.setEnvironment(new LinkedHashMap<>(environment));
            }
            if (workDir != null && !workDir.isBlank()) {
                config.setWorkDir(workDir);
            }
            if (encoding != null && !encoding.isBlank()) {
                config.setEncoding(encoding);
            }
            if (ptyType != null && !ptyType.isBlank()) {
                config.setPtyType(ptyType);
            }
            if (runAsUser != null) {
                config.setRunAsUser(validateRunAsUser(runAsUser));
            }
        } else {
            log.info("应用 [{}] 运行中, 仅应用名称/自动启动/自动重启可修改", id);
        }
        try {
            AtomicFileWriter.writeString(configPath(id), config.toToml());
            if (!running && proc != null) {
                processes.replace(id, proc, new AppProcess(config, this, outputBufferLines));
            }
        } catch (IOException e) {
            log.error("更新应用配置失败: {}", id, e);
            throw new IllegalStateException("更新应用配置失败: " + e.getMessage());
        }
        return toInfo(config);
        }
    }

    
    private static final java.util.regex.Pattern RUN_AS_USER_PATTERN =
            java.util.regex.Pattern.compile("[a-z_][a-z0-9_-]{0,31}");

    private static String validateRunAsUser(String runAsUser) {
        if (runAsUser == null || runAsUser.isBlank()) {
            return "";
        }
        String u = runAsUser.trim();
        if (!RUN_AS_USER_PATTERN.matcher(u).matches()) {
            throw new IllegalArgumentException("启动用户非法: " + u);
        }
        return u;
    }

    
    public boolean delete(String id) {
        if (!validId(id)) {
            return false;
        }
        synchronized (lockFor(id)) {
        AppProcess proc = processes.remove(id);
        if (proc != null) {
            proc.destroyNow();
        }
        Path dir = appsRoot.resolve(id);
        if (!Files.exists(dir)) {
            return false;
        }
        try {
            deleteRecursively(dir);
            log.info("应用已删除: {}", id);
            return true;
        } catch (IOException e) {
            log.error("删除应用目录失败: {}", dir, e);
            throw new IllegalStateException("删除应用目录失败: " + e.getMessage());
        }
        }
    }

    
    
    

    public AppInfo start(String id) {
        synchronized (lockFor(id)) {
        AppProcess proc = processes.computeIfAbsent(id, key -> {
            AppConfig cfg = loadConfig(key);
            return cfg == null ? null : new AppProcess(cfg, this, outputBufferLines);
        });
        if (proc == null) {
            return null;
        }
        proc.start();
        return toInfo(Objects.requireNonNull(loadConfig(id)));
        }
    }

    public AppInfo stop(String id) {
        synchronized (lockFor(id)) {
        AppProcess proc = processes.get(id);
        if (proc == null) {
            return get(id);
        }
        proc.stop();
        return toInfo(Objects.requireNonNull(loadConfig(id)));
        }
    }

    public AppInfo restart(String id) {
        synchronized (lockFor(id)) {
        AppProcess proc = processes.computeIfAbsent(id, key -> {
            AppConfig cfg = loadConfig(key);
            return cfg == null ? null : new AppProcess(cfg, this, outputBufferLines);
        });
        if (proc == null) {
            return null;
        }
        proc.restart();
        return toInfo(Objects.requireNonNull(loadConfig(id)));
        }
    }

    public AppInfo status(String id) {
        return get(id);
    }

    public List<String> logs(String id, int count) {
        AppProcess proc = processes.get(id);
        if (proc != null) {
            return proc.recentLog(Math.clamp(count, 1, 2000));
        }
        return new ArrayList<>();
    }

    
    public AppProcess getProcess(String id) {
        return processes.get(id);
    }

    
    public String workDirOf(String id) {
        AppConfig cfg = loadConfig(id);
        if (cfg == null || cfg.getWorkDir() == null || cfg.getWorkDir().isBlank()) {
            return null;
        }
        return cfg.getWorkDir();
    }

    public boolean protectAppFilesFromSymlinkEscape(String id) {
        AppConfig cfg = loadConfig(id);
        if (cfg == null) {
            throw new IllegalArgumentException("应用不存在: " + id);
        }
        return cfg.isProtectAppFilesFromSymlinkEscape();
    }

    
    public void notifyExit(AppProcess proc) {
        
        log.debug("应用进程退出回调: {}", proc.config().getName());
    }

    private Path configPath(String id) {
        return appsRoot.resolve(id).resolve("config").resolve("config.toml");
    }

    private Object lockFor(String id) {
        return appLocks.computeIfAbsent(id == null ? "" : id, ignored -> new Object());
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            var paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }
}
