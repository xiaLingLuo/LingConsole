package im.xz.cn.lingconsole.app.panel.service;

import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DownloadLimiter {

    private final int globalLimit;
    private final int perNodeLimit;
    private final int perUserLimit;
    private final Map<String, Integer> nodes = new HashMap<>();
    private final Map<String, Integer> users = new HashMap<>();
    private int active;

    public DownloadLimiter(int globalLimit, int perNodeLimit, int perUserLimit) {
        if (globalLimit < 1 || perNodeLimit < 1 || perUserLimit < 1
                || perNodeLimit > globalLimit || perUserLimit > globalLimit) {
            throw new IllegalArgumentException("下载并发限制无效");
        }
        this.globalLimit = globalLimit;
        this.perNodeLimit = perNodeLimit;
        this.perUserLimit = perUserLimit;
    }

    public synchronized Lease tryAcquire(String nodeId, String userId) {
        if (nodeId == null || userId == null || active >= globalLimit
                || nodes.getOrDefault(nodeId, 0) >= perNodeLimit
                || users.getOrDefault(userId, 0) >= perUserLimit) {
            return null;
        }
        active++;
        nodes.merge(nodeId, 1, Integer::sum);
        users.merge(userId, 1, Integer::sum);
        return new Lease(nodeId, userId);
    }

    private synchronized void release(String nodeId, String userId) {
        active--;
        decrement(nodes, nodeId);
        decrement(users, userId);
    }

    private static void decrement(Map<String, Integer> counts, String key) {
        int remaining = counts.getOrDefault(key, 0) - 1;
        if (remaining <= 0) counts.remove(key);
        else counts.put(key, remaining);
    }

    public final class Lease implements AutoCloseable {
        private final String nodeId;
        private final String userId;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(String nodeId, String userId) {
            this.nodeId = nodeId;
            this.userId = userId;
        }

        public InputStream wrap(InputStream input) {
            if (input == null) {
                close();
                throw new IllegalArgumentException("input is required");
            }
            return new FilterInputStream(input) {
                @Override
                public int read() throws IOException {
                    try {
                        int value = super.read();
                        if (value < 0) close();
                        return value;
                    } catch (IOException | RuntimeException e) {
                        close();
                        throw e;
                    }
                }

                @Override
                public int read(@NotNull byte[] bytes, int offset, int length) throws IOException {
                    try {
                        int count = super.read(bytes, offset, length);
                        if (count < 0) close();
                        return count;
                    } catch (IOException | RuntimeException e) {
                        close();
                        throw e;
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
            if (closed.compareAndSet(false, true)) {
                release(nodeId, userId);
            }
        }
    }
}
