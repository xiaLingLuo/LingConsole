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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;


public class AppProcess {

    private static final Logger log = LoggerFactory.getLogger(AppProcess.class);
    private static final int DEFAULT_OUTPUT_BUFFER_LINES = 2000;
    static final int MAX_OUTPUT_LINE_CHARS = 64 * 1024;

    private final AppConfig config;
    private final AppManager manager;

    private volatile Process process;
    private volatile AppStatus status = AppStatus.STOP;
    private volatile long startedAt;
    private long generation;
    private final AtomicInteger restartCount = new AtomicInteger();

    private final RingBuffer outputBuffer;


    private final List<Consumer<String>> outputListeners = new CopyOnWriteArrayList<>();
    
    private final List<Consumer<Boolean>> runningListeners = new CopyOnWriteArrayList<>();

    public AppProcess(AppConfig config, AppManager manager) {
        this(config, manager, DEFAULT_OUTPUT_BUFFER_LINES);
    }

    public AppProcess(AppConfig config, AppManager manager, int outputBufferLines) {
        this.config = config;
        this.manager = manager;
        this.outputBuffer = new RingBuffer(outputBufferLines);
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
        status = AppStatus.STARTING;
        try {
            List<String> tokens = buildCommandTokens();
            if (tokens.isEmpty()) {
                throw new IllegalStateException("命令为空");
            }
            tokens = wrapWithRunAsUser(tokens);
            Map<String, String> env = new HashMap<>(System.getenv());
            env.putAll(config.getEnvironment());
            String term = config.getPtyType();
            if (term == null || term.isBlank()) {
                term = "xterm-256color";
            }
            env.putIfAbsent("TERM", term);
            Process p = launchProcess(tokens, env);
            long processGeneration = ++generation;
            this.process = p;
            this.startedAt = System.currentTimeMillis() / 1000;
            this.status = AppStatus.RUNNING;
            outputBuffer.add("[LingConsole] 应用已启动。 PID=" + p.pid() + ", 可执行文件="
                    + executableName(tokens.getFirst()) + ", 参数数量=" + Math.max(0, tokens.size() - 1));
            startOutputReader(p);
            watchExit(p, processGeneration);
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
        stop(10);
    }

    public synchronized void stop(int gracefulWaitSeconds) {
        Process p = process;
        long stoppingGeneration = ++generation;
        if (p == null) {
            status = AppStatus.STOP;
            return;
        }
        if (!p.isAlive()) {
            process = null;
            status = AppStatus.STOP;
            notifyRunning(false);
            return;
        }
        status = AppStatus.STOPPING;
        outputBuffer.add("[LingConsole] 正在停止...");
        try {
            terminateProcessTree(p, Math.max(1, gracefulWaitSeconds), 3);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (generation == stoppingGeneration && process == p) {
                outputBuffer.add("[LingConsole] 应用已停止。");
                status = AppStatus.STOP;
                process = null;
                notifyRunning(false);
            }
        }
    }

    public synchronized void restart() {
        stop();
        start();
    }

    public synchronized void destroyNow() {
        Process p = process;
        long stoppingGeneration = ++generation;
        if (p != null && p.isAlive()) {
            try {
                terminateProcessTree(p, 1, 3);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (generation == stoppingGeneration && process == p) {
            process = null;
            status = AppStatus.STOP;
            if (p != null) {
                notifyRunning(false);
            }
        }
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
        return wrapWithRunAsUser(tokens, config.getRunAsUser(), isWindows());
    }

    static List<String> wrapWithRunAsUser(List<String> tokens, String runAsUser, boolean windows) {
        if (runAsUser == null || runAsUser.isBlank()) {
            return tokens;
        }
        if (windows) {
            throw new IllegalStateException("Windows 不支持 runAsUser=" + runAsUser
                    + ", 已拒绝启动以避免使用 daemon 身份运行");
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

    static String executableName(String executable) {
        if (executable == null || executable.isBlank()) {
            return "<unknown>";
        }
        int separator = Math.max(executable.lastIndexOf('/'), executable.lastIndexOf('\\'));
        String name = separator >= 0 ? executable.substring(separator + 1) : executable;
        return name.isBlank() ? "<unknown>" : name;
    }

    Process launchProcess(List<String> tokens, Map<String, String> env) throws IOException {
        PtyProcessBuilder builder = new PtyProcessBuilder()
                .setCommand(tokens.toArray(new String[0]))
                .setEnvironment(env)
                .setInitialColumns(120)
                .setInitialRows(30)
                .setConsole(false);
        if (config.getWorkDir() != null && !config.getWorkDir().isBlank()) {
            builder.setDirectory(config.getWorkDir());
        }
        return builder.start();
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
                    drainCompleteOutput(pending, outputBuffer::add);
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

    static void drainCompleteOutput(StringBuilder pending, Consumer<String> sink) {
        int newline;
        while ((newline = pending.indexOf("\n")) >= 0) {
            emitOutputSegment(pending, newline, true, sink);
        }
        while (pending.length() > MAX_OUTPUT_LINE_CHARS) {
            emitOutputSegment(pending, MAX_OUTPUT_LINE_CHARS, false, sink);
        }
    }

    private static void emitOutputSegment(StringBuilder pending, int length, boolean consumeNewline,
                                          Consumer<String> sink) {
        String line = pending.substring(0, length);
        pending.delete(0, length + (consumeNewline ? 1 : 0));
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }
        sink.accept(line);
    }

    private void watchExit(Process p, long processGeneration) {
        Thread watcher = new Thread(() -> {
            try {
                int exitCode = p.waitFor();
                handleExit(p, processGeneration, exitCode);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "app-watch-" + config.getId());
        watcher.setDaemon(true);
        watcher.start();
    }

    private synchronized void handleExit(Process exitedProcess, long exitedGeneration, int exitCode) {
        if (process != exitedProcess || generation != exitedGeneration) {
            return;
        }
        outputBuffer.add("[LingConsole] 进程退出, 退出码=" + exitCode);
        log.info("应用 [{}] 进程退出: code={}", config.getName(), exitCode);
        process = null;
        status = AppStatus.STOP;

        if (exitCode != 0 && config.isAutoRestart()) {
            int rc = restartCount.incrementAndGet();
            if (rc <= config.getMaxRestartCount()) {
                outputBuffer.add("[LingConsole] 自动重启 (" + rc + "/" + config.getMaxRestartCount() + ")");
                log.warn("应用 [{}] 自动重启 ({}/{})", config.getName(), rc, config.getMaxRestartCount());
                start();
                if (isRunning()) {
                    return;
                }
            } else {
                outputBuffer.add("[LingConsole] 超过最大重启次数, 停止");
            }
        }
        manager.notifyExit(this);
        notifyRunning(false);
    }

    private static void terminateProcessTree(Process process, int gracefulSeconds, int forceSeconds)
            throws InterruptedException {
        ProcessHandle root;
        try {
            root = process.toHandle();
        } catch (UnsupportedOperationException e) {
            process.destroy();
            if (!process.waitFor(gracefulSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(forceSeconds, TimeUnit.SECONDS);
            }
            return;
        }

        List<ProcessHandle> tree = processTree(root);
        if (isWindows() && root.isAlive()) {
            runTaskkill(root.pid(), false);
        }
        destroyHandles(tree, false);
        waitForExit(tree, gracefulSeconds);

        tree = mergeAlive(tree, processTree(root), root);
        if (tree.stream().anyMatch(ProcessHandle::isAlive)) {
            if (isWindows() && root.isAlive()) {
                runTaskkill(root.pid(), true);
            }
            destroyHandles(tree, true);
            waitForExit(tree, forceSeconds);
        }
    }

    private static List<ProcessHandle> processTree(ProcessHandle root) {
        List<ProcessHandle> handles = new ArrayList<>();
        try (Stream<ProcessHandle> descendants = root.descendants()) {
            handles.addAll(descendants.toList());
        } catch (RuntimeException e) {
            log.debug("无法枚举进程树: PID={}", root.pid(), e);
        }
        handles.add(root);
        return handles;
    }

    private static List<ProcessHandle> mergeAlive(List<ProcessHandle> first, List<ProcessHandle> second,
                                                   ProcessHandle root) {
        Map<Long, ProcessHandle> handles = new HashMap<>();
        Stream.concat(first.stream(), second.stream())
                .filter(ProcessHandle::isAlive)
                .forEach(handle -> handles.put(handle.pid(), handle));
        ProcessHandle liveRoot = handles.remove(root.pid());
        List<ProcessHandle> result = new ArrayList<>(handles.values());
        if (liveRoot != null) {
            result.add(liveRoot);
        }
        return result;
    }

    private static void destroyHandles(List<ProcessHandle> handles, boolean forcibly) {
        for (ProcessHandle handle : handles) {
            if (!handle.isAlive()) {
                continue;
            }
            try {
                if (forcibly) {
                    handle.destroyForcibly();
                } else {
                    handle.destroy();
                }
            } catch (RuntimeException e) {
                log.debug("终止进程失败: PID={}", handle.pid(), e);
            }
        }
    }

    private static void waitForExit(List<ProcessHandle> handles, int timeoutSeconds) throws InterruptedException {
        List<ProcessHandle> alive = handles.stream().filter(ProcessHandle::isAlive).toList();
        if (alive.isEmpty()) {
            return;
        }
        CompletableFuture<?> allExited = CompletableFuture.allOf(
                alive.stream().map(ProcessHandle::onExit).toArray(CompletableFuture[]::new));
        try {
            allExited.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            // 进程未在限定时间内退出, 交由调用方强制终止
        }
    }

    private static void runTaskkill(long pid, boolean forcibly) {
        List<String> command = taskkillCommand(pid, forcibly);
        try {
            Process taskkill = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!taskkill.waitFor(3, TimeUnit.SECONDS)) {
                taskkill.destroyForcibly();
            }
        } catch (IOException e) {
            log.debug("taskkill 执行失败: PID={}", pid, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static List<String> taskkillCommand(long pid, boolean forcibly) {
        List<String> command = new ArrayList<>(List.of("taskkill", "/PID", Long.toString(pid), "/T"));
        if (forcibly) {
            command.add("/F");
        }
        return command;
    }
}
