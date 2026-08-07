package im.xz.cn.lingconsole.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdUtilTest {

    @Test
    void generatedInitialPasswordsAlwaysMeetPasswordPolicy() {
        for (int i = 0; i < 1_000; i++) {
            String password = IdUtil.randomPassword();
            assertEquals(16, password.length());
            assertNull(PasswordPolicy.validate(password, null, "ling"));
        }
    }

    @Test
    void randomPasswordValidatesLength() {
        assertEquals(0, IdUtil.randomPassword(0).length());
        assertEquals(1, IdUtil.randomPassword(1).length());
        assertThrows(IllegalArgumentException.class, () -> IdUtil.randomPassword(-1));
    }
}
