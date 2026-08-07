package im.xz.cn.lingconsole.daemon;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class ExternalProcessRunner {

    private static final int MAX_CAPTURE_BYTES = 16 * 1024 * 1024;

    private ExternalProcessRunner() {
    }

    @FunctionalInterface
    interface Monitor {
        void check() throws IOException;
    }

    record Result(int exitCode, String stdout, String stderr, boolean timedOut, boolean outputTruncated) {
        Map<String, Object> asMap() {
            return Map.of("exitCode", exitCode, "stdout", stdout, "stderr", stderr,
                    "timedOut", timedOut, "outputTruncated", outputTruncated);
        }
    }

    static Result run(List<String> command, Duration timeout, Monitor monitor) throws IOException {
        Process process = new ProcessBuilder(command).start();
        LimitedOutput stdout = new LimitedOutput();
        LimitedOutput stderr = new LimitedOutput();
        Thread outThread = drain(process.getInputStream(), stdout, "archive-stdout");
        Thread errThread = drain(process.getErrorStream(), stderr, "archive-stderr");
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (System.nanoTime() >= deadline) {
                    ProcessTreeSupport.terminate(process);
                    join(outThread, errThread);
                    return new Result(-1, stdout.text(), "执行超时 (> " + timeout.toSeconds() + "s)",
                            true, stdout.truncated || stderr.truncated);
                }
                if (monitor != null) {
                    monitor.check();
                }
            }
            if (monitor != null) {
                monitor.check();
            }
            join(outThread, errThread);
            return new Result(process.exitValue(), stdout.text(), stderr.text(), false,
                    stdout.truncated || stderr.truncated);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ProcessTreeSupport.terminate(process);
            throw new IOException("等待外部进程时被中断", e);
        } catch (IOException | RuntimeException e) {
            ProcessTreeSupport.terminate(process);
            throw e;
        }
    }

    private static Thread drain(java.io.InputStream input, LimitedOutput output, String name) {
        Thread thread = new Thread(() -> {
            try (input) {
                input.transferTo(output);
            } catch (IOException ignored) {
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void join(Thread first, Thread second) {
        try {
            first.join(2000);
            second.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class LimitedOutput extends ByteArrayOutputStream {
        private boolean truncated;

        @Override
        public synchronized void write(@NotNull byte[] bytes, int offset, int length) {
            int remaining = MAX_CAPTURE_BYTES - count;
            if (remaining > 0) {
                super.write(bytes, offset, Math.min(remaining, length));
            }
            truncated |= length > remaining;
        }

        @Override
        public synchronized void write(int value) {
            if (count < MAX_CAPTURE_BYTES) {
                super.write(value);
            } else {
                truncated = true;
            }
        }

        synchronized String text() {
            return toString(StandardCharsets.UTF_8);
        }
    }
}
