package im.xz.cn.lingconsole.daemon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTaskLimiterTest {

    @Test
    void rejectsConcurrentTaskAndReleasesAfterCompletion() throws Exception {
        FileTaskLimiter limiter = new FileTaskLimiter(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread first = new Thread(() -> {
            try {
                limiter.tryRun(() -> {
                    entered.countDown();
                    release.await(2, TimeUnit.SECONDS);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        first.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS));

        AtomicBoolean ran = new AtomicBoolean();
        assertFalse(limiter.tryRun(() -> ran.set(true)));
        assertFalse(ran.get());
        release.countDown();
        first.join(2000);
        assertTrue(limiter.tryRun(() -> ran.set(true)));
    }

    @Test
    void releasesPermitAfterException() throws Exception {
        FileTaskLimiter limiter = new FileTaskLimiter(1);
        assertThrows(IOException.class, () -> limiter.tryRun(() -> {
            throw new IOException("failed");
        }));
        assertTrue(limiter.tryRun(() -> { }));
    }
}
