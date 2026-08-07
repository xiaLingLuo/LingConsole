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
package im.xz.cn.lingconsole.daemon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaemonRateLimiterTest {

    @Test
    void authLockoutAfterFailureLimitAndReset() {
        DaemonApp.DaemonRateLimiter rl = new DaemonApp.DaemonRateLimiter();
        for (int i = 0; i < 5; i++) {
            rl.recordAuthFailure("1.2.3.4");
        }
        assertTrue(rl.isAuthLocked("1.2.3.4"));
        rl.resetAuthFailure("1.2.3.4");
        assertFalse(rl.isAuthLocked("1.2.3.4"));
    }

    @Test
    void authLockoutIsPerIp() {
        DaemonApp.DaemonRateLimiter rl = new DaemonApp.DaemonRateLimiter();
        for (int i = 0; i < 5; i++) {
            rl.recordAuthFailure("1.2.3.4");
        }
        assertTrue(rl.isAuthLocked("1.2.3.4"));
        assertFalse(rl.isAuthLocked("5.6.7.8"), "其他 IP 不受影响");
    }

    @Test
    void publicEndpointRateLimited() {
        DaemonApp.DaemonRateLimiter rl = new DaemonApp.DaemonRateLimiter();
        boolean exceeded = false;
        for (int i = 0; i < 65; i++) {
            if (!rl.allowPublic("1.2.3.4")) {
                exceeded = true;
                break;
            }
        }
        assertTrue(exceeded, "每分钟 60 次之后应限速");
    }

    @Test
    void disabledSuccessUnlockPreservesLockoutWithoutCheckingKey() {
        DaemonApp.DaemonRateLimiter rl = lockedLimiter("1.2.3.4");
        AtomicInteger checks = new AtomicInteger();

        assertEquals(DaemonApp.DaemonRateLimiter.AuthResult.LOCKED,
                rl.authenticate("1.2.3.4", false, () -> {
                    checks.incrementAndGet();
                    return true;
                }));
        assertEquals(0, checks.get());
        assertTrue(rl.isAuthLocked("1.2.3.4"));
    }

    @Test
    void enabledSuccessUnlockCanOnlyBeConsumedOncePerWindow() {
        DaemonApp.DaemonRateLimiter rl = lockedLimiter("1.2.3.4");

        assertEquals(DaemonApp.DaemonRateLimiter.AuthResult.LOCKED,
                rl.authenticate("1.2.3.4", true, () -> false));
        assertEquals(DaemonApp.DaemonRateLimiter.AuthResult.SUCCESS,
                rl.authenticate("1.2.3.4", true, () -> true));
        assertEquals(DaemonApp.DaemonRateLimiter.AuthResult.LOCKED,
                rl.authenticate("1.2.3.4", true, () -> true));
        assertTrue(rl.isAuthLocked("1.2.3.4"));
    }

    @Test
    void successUnlockResetsAfterLockWindowExpires() {
        AtomicLong now = new AtomicLong(1_000_000L);
        DaemonApp.DaemonRateLimiter rl = new DaemonApp.DaemonRateLimiter(now::get);
        lock(rl, "1.2.3.4");
        assertEquals(DaemonApp.DaemonRateLimiter.AuthResult.SUCCESS,
                rl.authenticate("1.2.3.4", true, () -> true));

        now.addAndGet(10 * 60_000L + 1);
        assertFalse(rl.isAuthLocked("1.2.3.4"));
        lock(rl, "1.2.3.4");
        assertEquals(DaemonApp.DaemonRateLimiter.AuthResult.SUCCESS,
                rl.authenticate("1.2.3.4", true, () -> true));
    }

    @Test
    void lockedVerificationIsProtectedByPublicLimiter() {
        DaemonApp.DaemonRateLimiter rl = lockedLimiter("1.2.3.4");
        for (int i = 0; i < 60; i++) {
            assertTrue(rl.allowPublic("1.2.3.4"));
        }
        AtomicInteger checks = new AtomicInteger();

        assertEquals(DaemonApp.DaemonRateLimiter.AuthResult.LOCKED,
                rl.authenticate("1.2.3.4", true, () -> {
                    checks.incrementAndGet();
                    return true;
                }));
        assertEquals(0, checks.get());
    }

    @Test
    void concurrentHttpAndSocketAttemptsConsumeOneSharedUnlock() throws Exception {
        DaemonApp.DaemonRateLimiter rl = lockedLimiter("1.2.3.4");
        int attempts = 20;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(attempts);
        List<Future<DaemonApp.DaemonRateLimiter.AuthResult>> results = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return rl.authenticate("1.2.3.4", true, () -> true);
                }));
            }
            ready.await();
            start.countDown();

            long successes = 0;
            for (Future<DaemonApp.DaemonRateLimiter.AuthResult> result : results) {
                if (result.get() == DaemonApp.DaemonRateLimiter.AuthResult.SUCCESS) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        } finally {
            executor.shutdownNow();
        }
    }

    private static DaemonApp.DaemonRateLimiter lockedLimiter(String ip) {
        DaemonApp.DaemonRateLimiter rl = new DaemonApp.DaemonRateLimiter();
        lock(rl, ip);
        return rl;
    }

    private static void lock(DaemonApp.DaemonRateLimiter rl, String ip) {
        for (int i = 0; i < 5; i++) {
            rl.recordAuthFailure(ip);
        }
    }
}
