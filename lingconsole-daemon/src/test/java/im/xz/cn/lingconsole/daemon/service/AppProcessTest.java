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
import im.xz.cn.lingconsole.daemon.model.AppStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppProcessTest {

    @Test
    void splitsSimpleCommand() {
        assertEquals(List.of("java", "-jar", "server.jar"),
                AppProcess.splitCommandLine("java -jar server.jar"));
    }

    @Test
    void handlesQuotedArguments() {
        assertEquals(List.of("java", "-jar", "my server.jar", "--port", "25565"),
                AppProcess.splitCommandLine("java -jar \"my server.jar\" --port 25565"));
    }

    @Test
    void handlesMultipleSpaces() {
        assertEquals(List.of("echo", "hello", "world"),
                AppProcess.splitCommandLine("  echo    hello   world  "));
    }

    @Test
    void emptyCommand() {
        assertEquals(List.of(), AppProcess.splitCommandLine(""));
        assertEquals(List.of(), AppProcess.splitCommandLine("   "));
    }

    @Test
    void startupDescriptionUsesExecutableNameWithoutArguments() {
        assertEquals("java.exe", AppProcess.executableName("C:\\runtime\\java.exe"));
        assertFalse(AppProcess.executableName("C:\\runtime\\java.exe").contains("runtime"));
    }

    @Test
    void wrapWithRunAsPrependsRunuser() {
        List<String> wrapped = AppProcess.wrapWithRunAs(
                List.of("java", "-jar", "server.jar"), "www-data");
        assertEquals(List.of("runuser", "-u", "www-data", "--", "java", "-jar", "server.jar"),
                wrapped);
    }

    @Test
    void windowsRunAsUserFailsClosedWithClearError() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> AppProcess.wrapWithRunAsUser(List.of("server.exe"), "service-user", true));

        assertTrue(error.getMessage().contains("Windows"));
        assertTrue(error.getMessage().contains("runAsUser=service-user"));
        assertTrue(error.getMessage().contains("拒绝启动"));
    }

    @Test
    void windowsTreeKillUsesTreeAndForceFlags() {
        assertEquals(List.of("taskkill", "/PID", "4242", "/T"),
                AppProcess.taskkillCommand(4242, false));
        assertEquals(List.of("taskkill", "/PID", "4242", "/T", "/F"),
                AppProcess.taskkillCommand(4242, true));
    }

    @Test
    void staleWatcherCannotOverwriteNewGeneration() throws Exception {
        ControlledProcess first = new ControlledProcess(1001);
        ControlledProcess second = new ControlledProcess(1002);
        TestAppProcess app = testApp(false, first, second);
        CountDownLatch stopped = new CountDownLatch(1);
        app.addRunningListener(running -> {
            if (!running) {
                stopped.countDown();
            }
        });

        app.start();
        first.exit(0, false);
        assertTrue(first.watcherWaiting.await(1, TimeUnit.SECONDS));
        app.start();
        first.releaseWatcher();

        assertFalse(stopped.await(300, TimeUnit.MILLISECONDS));
        assertEquals(AppStatus.RUNNING, app.status());
        assertEquals(1002L, app.pid());
        assertSame(second, app.launched.get(1));
        app.destroyNow();
    }

    @Test
    void stopInvalidatesPendingAutoRestart() throws Exception {
        ControlledProcess first = new ControlledProcess(2001);
        ControlledProcess unexpectedRestart = new ControlledProcess(2002);
        TestAppProcess app = testApp(true, first, unexpectedRestart);

        app.start();
        first.exit(1, false);
        assertTrue(first.watcherWaiting.await(1, TimeUnit.SECONDS));
        app.stop();
        first.releaseWatcher();

        assertFalse(app.secondLaunch.await(300, TimeUnit.MILLISECONDS));
        assertEquals(1, app.launched.size());
        assertEquals(AppStatus.STOP, app.status());
        assertFalse(app.isRunning());
    }

    @Test
    void configuredOutputBufferCapacityIsApplied() {
        AppConfig config = new AppConfig();
        config.setId("buffer-test");
        config.setName("buffer-test");
        config.setCommand("");
        AppProcess app = new AppProcess(config, new AppManager("."), 2);

        app.start();
        app.start();
        app.start();

        assertEquals(2, app.recentLog(10).size());
    }

    @Test
    void splitsUnterminatedOutputToBoundPendingMemory() {
        StringBuilder pending = new StringBuilder("x".repeat(AppProcess.MAX_OUTPUT_LINE_CHARS * 2 + 17));
        List<String> segments = new java.util.ArrayList<>();

        AppProcess.drainCompleteOutput(pending, segments::add);

        assertEquals(2, segments.size());
        assertTrue(segments.stream().allMatch(s -> s.length() == AppProcess.MAX_OUTPUT_LINE_CHARS));
        assertEquals(17, pending.length());
    }

    private static TestAppProcess testApp(boolean autoRestart, ControlledProcess... processes) {
        AppConfig config = new AppConfig();
        config.setId("race-test");
        config.setName("race-test");
        config.setCommand("test-command");
        config.setAutoRestart(autoRestart);
        config.setMaxRestartCount(3);
        return new TestAppProcess(config, List.of(processes));
    }

    private static final class TestAppProcess extends AppProcess {
        private final List<ControlledProcess> available;
        private final AtomicInteger next = new AtomicInteger();
        private final List<ControlledProcess> launched = new CopyOnWriteArrayList<>();
        private final CountDownLatch secondLaunch = new CountDownLatch(1);

        private TestAppProcess(AppConfig config, List<ControlledProcess> available) {
            super(config, new AppManager("."));
            this.available = available;
        }

        @Override
        Process launchProcess(List<String> tokens, Map<String, String> env) {
            ControlledProcess process = available.get(next.getAndIncrement());
            launched.add(process);
            if (launched.size() == 2) {
                secondLaunch.countDown();
            }
            return process;
        }
    }

    private static final class ControlledProcess extends Process {
        private final long pid;
        private final CountDownLatch exited = new CountDownLatch(1);
        private final CountDownLatch watcherWaiting = new CountDownLatch(1);
        private final CountDownLatch watcherRelease = new CountDownLatch(1);
        private volatile boolean alive = true;
        private volatile int exitCode;

        private ControlledProcess(long pid) {
            this.pid = pid;
        }

        private void exit(int code, boolean releaseWatcher) {
            exitCode = code;
            alive = false;
            exited.countDown();
            if (releaseWatcher) {
                watcherRelease.countDown();
            }
        }

        private void releaseWatcher() {
            watcherRelease.countDown();
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() throws InterruptedException {
            exited.await();
            watcherWaiting.countDown();
            watcherRelease.await();
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            return exited.await(timeout, unit);
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException();
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            exit(143, true);
        }

        @Override
        public Process destroyForcibly() {
            exit(137, true);
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public long pid() {
            return pid;
        }

        @Override
        public ProcessHandle toHandle() {
            throw new UnsupportedOperationException("controlled process");
        }
    }
}
