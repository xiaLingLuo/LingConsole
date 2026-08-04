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

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import im.xz.cn.lingconsole.daemon.model.AppConfig;
import im.xz.cn.lingconsole.daemon.model.AppStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


public class AppProcess {

    private static final Logger log = LoggerFactory.getLogger(AppProcess.class);
    private static final int OUTPUT_BUFFER_LINES = 2000;

    private final AppConfig config;
    private final AppManager manager;

    private volatile Process process;
    private volatile AppStatus status = AppStatus.STOP;
    private volatile long startedAt;
    private final AtomicInteger restartCount = new AtomicInteger();

    private final RingBuffer outputBuffer = new RingBuffer(OUTPUT_BUFFER_LINES);


    private final List<Consumer<String>> outputListeners = new CopyOnWriteArrayList<>();
    
    private final List<Consumer<Boolean>> runningListeners = new CopyOnWriteArrayList<>();

    public AppProcess(AppConfig config, AppManager manager) {
        this.config = config;
        this.manager = manager;
    }

    public void addOutputListener(Consumer<String> listener) {
        outputListeners.add(listener);
    }

    public void removeOutputListener(Consumer<String> listener) {
        outputListeners.remove(listener);
    }

    public void addRunningListener(Consumer<Boolean> listener) {
        runningListeners.add(listener);
    }

    public void removeRunningListener(Consumer<Boolean> listener) {
        runningListeners.remove(listener);
    }

    private void notifyRunning(boolean running) {
        for (Consumer<Boolean> listener : runningListeners) {
            try {
                listener.accept(running);
            } catch (Exception e) {
                log.debug("运行状态监听器异常", e);
            }
        }
    }

    
    public void sendInput(String data) {
        Process p = process;
        if (p == null || !p.isAlive() || data == null) {
            return;
        }
        try {
            OutputStream os = p.getOutputStream();
            os.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            log.warn("应用 [{}] 输入写入失败", config.getName());
        }
    }

    public AppConfig config() {
        return config;
    }

    public AppStatus status() {
        return status;
    }

    public long startedAt() {
        return startedAt;
    }

    public int restartCount() {
        return restartCount.get();
    }

    public Long pid() {
        Process p = process;
        return p != null && p.isAlive() ? p.pid() : null;
    }

    public boolean isRunning() {
        Process p = process;
        return p != null && p.isAlive();
    }

    public List<String> recentLog(int count) {
        return outputBuffer.recent(count);
    }

    
    
    

    
    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        List<String> tokens = buildCommandTokens();
        if (tokens.isEmpty()) {
            log.warn("应用 [{}] 启动命令为空, 无法启动", config.getName());
            outputBuffer.add("[LingConsole] 启动失败: 命令为空");
            return;
        }
        tokens = wrapWithRunAsUser(tokens);
        if (tokens.isEmpty()) {
            log.warn("应用 [{}] 启动命令为空, 无法启动", config.getName());
            outputBuffer.add("[LingConsole] 启动失败: 命令为空");
            return;
        }
        status = AppStatus.STARTING;        try {
            
            Map<String, String> env = new HashMap<>(System.getenv());
            env.putAll(config.getEnvironment());
            String term = config.getPtyType();
            if (term == null || term.isBlank()) {
                term = "xterm-256color";
            }
            env.putIfAbsent("TERM", term);
            PtyProcessBuilder builder = new PtyProcessBuilder()
                    .setCommand(tokens.toArray(new String[0]))
                    .setEnvironment(env)
                    .setInitialColumns(120)
                    .setInitialRows(30)
                    .setConsole(false);
            if (config.getWorkDir() != null && !config.getWorkDir().isBlank()) {
                builder.setDirectory(config.getWorkDir());
            }
            PtyProcess p = builder.start();
            this.process = p;
            this.startedAt = System.currentTimeMillis() / 1000;
            this.status = AppStatus.RUNNING;
            outputBuffer.add("[LingConsole] 应用已启动。 PID=" + p.pid() + ", 命令: " + String.join(" ", tokens));
            startOutputReader(p);
            watchExit(p);
            notifyRunning(true);
            log.info("应用 [{}] 已启动: PID={}", config.getName(), p.pid());
        } catch (Exception e) {
            this.process = null;
            this.status = AppStatus.STOP;
            outputBuffer.add("[LingConsole] 启动失败: " + e.getMessage());
            log.error("应用 [{}] 启动失败", config.getName(), e);
        }
    }

    
    public synchronized void stop() {
        Process p = process;
        if (p == null || !p.isAlive()) {
            status = AppStatus.STOP;
            return;
        }
        status = AppStatus.STOPPING;
        outputBuffer.add("[LingConsole] 正在停止...");
        try {
            p.destroy(); 
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                outputBuffer.add("[LingConsole] 软终止超时, 强制结束");
                p.destroyForcibly();
                p.waitFor(3, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (p.isAlive()) {
                p.destroyForcibly();
            }
            outputBuffer.add("[LingConsole] 应用已停止。");
            status = AppStatus.STOP;
            process = null;
            notifyRunning(false);
        }
    }

    public synchronized void restart() {
        stop();
        start();
    }

    public void destroyNow() {
        Process p = process;
        if (p != null) {
            p.destroyForcibly();
        }
        process = null;
        status = AppStatus.STOP;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    
    
    

    private List<String> buildCommandTokens() {
        List<String> tokens = new ArrayList<>();
        String command = config.getCommand();
        if (command != null && !command.isBlank()) {
            tokens.addAll(splitCommandLine(command.trim()));
        }
        if (config.getArgs() != null) {
            tokens.addAll(config.getArgs());
        }
        return tokens;
    }

    
    private List<String> wrapWithRunAsUser(List<String> tokens) {
        String runAsUser = config.getRunAsUser();
        if (runAsUser == null || runAsUser.isBlank()) {
            return tokens;
        }
        if (isWindows()) {
            log.warn("应用 [{}] 指定了启动用户 [{}], 但 Windows 暂不支持, 将使用当前用户运行",
                    config.getName(), runAsUser);
            return tokens;
        }
        return wrapWithRunAs(tokens, runAsUser);
    }

    
    static List<String> wrapWithRunAs(List<String> tokens, String runAsUser) {
        List<String> wrapped = new ArrayList<>(tokens.size() + 3);
        wrapped.add("runuser");
        wrapped.add("-u");
        wrapped.add(runAsUser);
        wrapped.add("--");
        wrapped.addAll(tokens);
        return wrapped;
    }

    
    static List<String> splitCommandLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ' ' && !inQuote) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private void startOutputReader(Process p) {
        Charset charset;
        try {
            charset = Charset.forName(config.getEncoding());
        } catch (Exception e) {
            charset = StandardCharsets.UTF_8;
        }
        final Charset cs = charset;
        InputStream in = p.getInputStream();
        Thread outputReaderThread = new Thread(() -> {
            byte[] buffer = new byte[8192];
            StringBuilder pending = new StringBuilder();
            try {
                int n;
                while ((n = in.read(buffer)) != -1) {
                    String text = new String(buffer, 0, n, cs);

                    for (Consumer<String> listener : outputListeners) {
                        try {
                            listener.accept(text);
                        } catch (Exception e) {
                            log.debug("输出监听器异常", e);
                        }
                    }

                    pending.append(text);
                    int idx;
                    while ((idx = pending.indexOf("\n")) != -1) {
                        String line = pending.substring(0, idx);
                        pending.delete(0, idx + 1);
                        if (line.endsWith("\r")) {
                            line = line.substring(0, line.length() - 1);
                        }
                        outputBuffer.add(line);
                    }
                }
                if (!pending.isEmpty()) {
                    String tail = pending.toString();
                    if (tail.endsWith("\r")) {
                        tail = tail.substring(0, tail.length() - 1);
                    }
                    if (!tail.isEmpty()) {
                        outputBuffer.add(tail);
                    }
                }
            } catch (IOException _) {

            }
        }, "app-output-" + config.getId());
        outputReaderThread.setDaemon(true);
        outputReaderThread.start();
    }

    private void watchExit(Process p) {
        Thread watcher = new Thread(() -> {
            try {
                int exitCode = p.waitFor();
                if (status != AppStatus.STOPPING) {
                    outputBuffer.add("[LingConsole] 进程退出, 退出码=" + exitCode);
                    log.info("应用 [{}] 进程退出: code={}", config.getName(), exitCode);
                }
                
                if (status == AppStatus.RUNNING && exitCode != 0 && config.isAutoRestart()) {
                    int rc = restartCount.incrementAndGet();
                    if (rc <= config.getMaxRestartCount()) {
                        outputBuffer.add("[LingConsole] 自动重启 (" + rc + "/" + config.getMaxRestartCount() + ")");
                        log.warn("应用 [{}] 自动重启 ({}/{})", config.getName(), rc, config.getMaxRestartCount());
                        start();
                        return;
                    }
                    outputBuffer.add("[LingConsole] 超过最大重启次数, 停止");
                }
                status = AppStatus.STOP;
                process = null;
                manager.notifyExit(this);
                notifyRunning(false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "app-watch-" + config.getId());
        watcher.setDaemon(true);
        watcher.start();
    }
}
