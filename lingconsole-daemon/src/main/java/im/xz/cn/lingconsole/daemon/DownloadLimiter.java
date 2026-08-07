package im.xz.cn.lingconsole.daemon;

import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

final class DownloadLimiter {

    private final Semaphore permits;
    private final long idleTimeoutNanos;
    private final long maxDurationNanos;

    DownloadLimiter(int maxConcurrent, Duration idleTimeout, Duration maxDuration) {
        permits = new Semaphore(maxConcurrent);
        idleTimeoutNanos = idleTimeout.toNanos();
        maxDurationNanos = maxDuration.toNanos();
    }

    Lease tryAcquire() {
        return permits.tryAcquire() ? new Lease() : null;
    }

    int availablePermits() {
        return permits.availablePermits();
    }

    final class Lease implements AutoCloseable {
        private final AtomicBoolean released = new AtomicBoolean();

        InputStream protect(InputStream upstream) {
            return new FilterInputStream(upstream) {
                private final long started = System.nanoTime();
                private long lastRead = started;

                @Override
                public int read() throws IOException {
                    checkDeadline();
                    try {
                        int value = super.read();
                        afterRead(value < 0);
                        return value;
                    } catch (IOException | RuntimeException e) {
                        closeAfterFailure(e);
                        throw e;
                    }
                }

                @Override
                public int read(@NotNull byte[] bytes, int offset, int length) throws IOException {
                    checkDeadline();
                    try {
                        int count = super.read(bytes, offset, length);
                        afterRead(count < 0);
                        return count;
                    } catch (IOException | RuntimeException e) {
                        closeAfterFailure(e);
                        throw e;
                    }
                }

                private void checkDeadline() throws IOException {
                    long now = System.nanoTime();
                    if (now - started > maxDurationNanos) {
                        IOException error = new IOException("下载超过最大持续时间");
                        closeAfterFailure(error);
                        throw error;
                    }
                    if (now - lastRead > idleTimeoutNanos) {
                        IOException error = new IOException("下载读取空闲超时");
                        closeAfterFailure(error);
                        throw error;
                    }
                }

                private void afterRead(boolean eof) throws IOException {
                    lastRead = System.nanoTime();
                    if (eof) {
                        close();
                    }
                }

                private void closeAfterFailure(Exception original) {
                    try {
                        close();
                    } catch (IOException closeError) {
                        original.addSuppressed(closeError);
                    }
                }

                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        Lease.this.close();
                    }
                }
            };
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }
}
