package im.xz.cn.lingconsole.app.panel.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DownloadLimiterTest {

    @Test
    void enforcesGlobalNodeAndUserLimits() {
        DownloadLimiter limiter = new DownloadLimiter(3, 2, 1);
        DownloadLimiter.Lease first = limiter.tryAcquire("node-a", "user-a");
        DownloadLimiter.Lease second = limiter.tryAcquire("node-a", "user-b");
        assertNotNull(first);
        assertNotNull(second);
        assertNull(limiter.tryAcquire("node-b", "user-a"), "同一用户应受限");
        assertNull(limiter.tryAcquire("node-a", "user-c"), "同一节点应受限");

        DownloadLimiter.Lease third = limiter.tryAcquire("node-b", "user-c");
        assertNotNull(third);
        assertNull(limiter.tryAcquire("node-c", "user-d"), "全局应受限");
        first.close();
        assertNotNull(limiter.tryAcquire("node-c", "user-a"));
        second.close();
        third.close();
    }

    @Test
    void streamReleasesLeaseOnEofCloseAndReadFailure() throws Exception {
        DownloadLimiter limiter = new DownloadLimiter(1, 1, 1);
        InputStream eof = limiter.tryAcquire("node", "user")
                .wrap(new ByteArrayInputStream(new byte[]{1}));
        assertEquals(1, eof.read());
        assertEquals(-1, eof.read());
        assertNotNull(limiter.tryAcquire("node", "user"));

        DownloadLimiter closeLimiter = new DownloadLimiter(1, 1, 1);
        InputStream closed = closeLimiter.tryAcquire("node", "user")
                .wrap(new ByteArrayInputStream(new byte[]{1}));
        closed.close();
        assertNotNull(closeLimiter.tryAcquire("node", "user"));

        DownloadLimiter failureLimiter = new DownloadLimiter(1, 1, 1);
        InputStream failed = failureLimiter.tryAcquire("node", "user").wrap(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("boom"); }
        });
        assertThrows(IOException.class, failed::read);
        assertNotNull(failureLimiter.tryAcquire("node", "user"));
    }
}
