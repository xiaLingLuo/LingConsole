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
package im.xz.cn.lingconsole.app.panel.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    @Test
    void ipRateLimitTriggersAfterRatePerSecond() {
        LoginAttemptService svc = new LoginAttemptService(5, 900, 8);
        for (int i = 0; i < 8; i++) {
            assertFalse(svc.isIpRateExceeded("1.2.3.4"), "第 " + (i + 1) + " 次不应超限");
        }
        assertTrue(svc.isIpRateExceeded("1.2.3.4"), "超过每秒上限应被限速");
    }

    @Test
    void differentIpsAreIndependent() {
        LoginAttemptService svc = new LoginAttemptService(5, 900, 1);
        assertFalse(svc.isIpRateExceeded("1.2.3.4"));
        assertFalse(svc.isIpRateExceeded("5.6.7.8"), "不同 IP 互不影响");
        assertTrue(svc.isIpRateExceeded("1.2.3.4"));
    }

    @Test
    void lockoutAfterFailuresAndReset() {
        LoginAttemptService svc = new LoginAttemptService(3, 60, 8);
        for (int i = 0; i < 3; i++) {
            svc.recordFailure("1.2.3.4", "alice");
        }
        assertTrue(svc.isLocked("1.2.3.4", "alice"));
        assertTrue(svc.remainingSeconds("1.2.3.4", "alice") > 0);
        svc.reset("1.2.3.4", "alice");
        assertFalse(svc.isLocked("1.2.3.4", "alice"));
    }

    @Test
    void ipCountsBoundedByCapacity() throws Exception {
        LoginAttemptService svc = new LoginAttemptService(5, 900, 8);
        for (int i = 0; i < 50_100; i++) {
            svc.isIpRateExceeded("ip-" + i);
        }
        Field f = LoginAttemptService.class.getDeclaredField("ipCounts");
        f.setAccessible(true);
        Map<?, ?> map = (Map<?, ?>) f.get(svc);
        assertTrue(map.size() <= 50_000, "ipCounts 不得超过容量上限, 实际 " + map.size());
    }
}
