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
import im.xz.cn.lingconsole.common.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class TerminalService {

    private static final Logger log = LoggerFactory.getLogger(TerminalService.class);

    
    public interface OutputListener {
        void onOutput(String data);

        void onExit();

        
        default void onStatus(boolean running) {
        }
    }

    
    public interface TerminalSession {
        String id();

        void setOutputListener(OutputListener listener);

        void writeInput(String data);

        void resize(int cols, int rows);

        void close();

        String description();

        
        default List<String> recentLines() {
            return List.of();
        }

        
        default boolean canInput() {
            return true;
        }
    }

    private final Map<String, TerminalSession> sessions = new ConcurrentHashMap<>();
    private final String shellMode;

    public TerminalService() {
        this("auto");
    }

    public TerminalService(String shellMode) {
        this.shellMode = shellMode == null || shellMode.isBlank() ? "auto" : shellMode;
    }

    public void stop() {
        sessions.values().forEach(TerminalSession::close);
        sessions.clear();
    }

    public TerminalSession get(String id) {
        return sessions.get(id);
    }

    public void remove(String id) {
        TerminalSession session = sessions.remove(id);
        if (session != null) {
            session.close();
        }
    }

    
    
    

    
    public TerminalSession createShell(int cols, int rows) {
        PtyTerminal terminal = new PtyTerminal(cols, rows);
        sessions.put(terminal.id(), terminal);
        return terminal;
    }

    
    public TerminalSession createAppTerminal(AppProcess appProcess) {
        AppTerminal terminal = new AppTerminal(appProcess);
        sessions.put(terminal.id(), terminal);
        return terminal;
    }

    
    
    

    private final class PtyTerminal implements TerminalSession {
        private final String id;
        private final PtyProcess process;
        private final OutputStream input;
        private final Thread readerThread;
        private volatile OutputListener listener;
        private volatile boolean closed;

        PtyTerminal(int cols, int rows) {
            this.id = IdUtil.uuid();
            PtyProcess p = null;
            try {
                PtyProcessBuilder builder = new PtyProcessBuilder()
                        .setCommand(shellCommand())
                        .setDirectory(systemRootDir())
                        .setInitialColumns(Math.max(cols, 20))
                        .setInitialRows(Math.max(rows, 5))
                        .setConsole(isWindows());
                p = builder.start();
            } catch (Exception e) {
                log.error("创建 PTY 失败", e);
            }
            this.process = p;
            this.input = p == null ? null : p.getOutputStream();

            if (process != null) {
                InputStream out = process.getInputStream();
                readerThread = new Thread(() -> readLoop(out), "pty-read-" + id.substring(0, 8));
                readerThread.setDaemon(true);
                readerThread.start();
            } else {
                readerThread = null;
            }
        }

        private void readLoop(InputStream in) {
            byte[] buffer = new byte[8192];
            try {
                int n;
                while (!closed && (n = in.read(buffer)) != -1) {
                    if (listener != null) {
                        listener.onOutput(new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException _) {
                
            } finally {
                closed = true;
                if (listener != null) {
                    listener.onExit();
                }
                sessions.remove(id);
            }
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void setOutputListener(OutputListener listener) {
            this.listener = listener;
        }

        @Override
        public void writeInput(String data) {
            if (process == null || input == null || closed) {
                return;
            }
            try {
                input.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                input.flush();
            } catch (IOException e) {
                log.warn("终端输入写入失败: {}", id);
            }
        }

        @Override
        public void resize(int cols, int rows) {
            if (process != null) {
                process.setWinSize(new com.pty4j.WinSize(Math.max(cols, 20), Math.max(rows, 5)));
            }
        }

        @Override
        public void close() {
            closed = true;
            if (process != null) {
                process.destroy();
            }
            sessions.remove(id);
        }

        @Override
        public String description() {
            return "shell(pty)";
        }
    }

    private static final class AppTerminal implements TerminalSession {
        private final String id;
        private final AppProcess appProcess;
        private volatile OutputListener listener;
        private volatile boolean closed;

        AppTerminal(AppProcess appProcess) {
            this.id = IdUtil.uuid();
            this.appProcess = appProcess;
            appProcess.addOutputListener(this::onAppOutput);
            appProcess.addRunningListener(this::onAppRunning);
        }

        private void onAppOutput(String text) {
            if (!closed && listener != null) {
                listener.onOutput(text);
            }
        }

        private void onAppRunning(boolean running) {
            if (closed) {
                return;
            }
            if (!running) {
                closed = true;
                appProcess.removeOutputListener(this::onAppOutput);
                appProcess.removeRunningListener(this::onAppRunning);
                if (listener != null) {
                    listener.onStatus(false);
                    listener.onOutput("[LingConsole] 应用已停止。\r\n");
                    listener.onExit();
                }
                return;
            }
            if (listener != null) {
                listener.onStatus(true);
                listener.onOutput("[LingConsole] 应用已启动。\r\n");
            }
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void setOutputListener(OutputListener listener) {
            this.listener = listener;
        }

        @Override
        public void writeInput(String data) {
            if (!closed) {
                appProcess.sendInput(data);
            }
        }

        @Override
        public void resize(int cols, int rows) {
            
        }

        @Override
        public void close() {
            closed = true;
            appProcess.removeOutputListener(this::onAppOutput);
            appProcess.removeRunningListener(this::onAppRunning);
        }

        @Override
        public String description() {
            return "app:" + appProcess.config().getName();
        }

        @Override
        public List<String> recentLines() {
            return appProcess.recentLog(200);
        }

        @Override
        public boolean canInput() {
            return appProcess.isRunning();
        }
    }

    

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    
    private static String systemRootDir() {
        try {
            java.nio.file.Path root = java.nio.file.Paths.get("").toAbsolutePath().getRoot();
            return root == null ? "/" : root.toString();
        } catch (Exception e) {
            return "/";
        }
    }

    
    private String[] shellCommand() {
        if (isWindows()) {
            
            return new String[]{"cmd.exe"};
        }
        boolean useLogin = "login".equals(shellMode);
        if (useLogin && java.nio.file.Files.isExecutable(java.nio.file.Path.of("/bin/login"))) {
            return new String[]{"/bin/login"};
        }
        String shell = System.getenv("SHELL");
        return new String[]{shell == null || shell.isBlank() ? "/bin/bash" : shell};
    }
}
