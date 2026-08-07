package im.xz.cn.lingconsole.app.panel.service;

import im.xz.cn.lingconsole.app.panel.repository.DatabaseManager;
import im.xz.cn.lingconsole.app.panel.repository.LogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogServiceTest {

    @TempDir
    Path temporary;

    @Test
    void defaultsToCoreLogsAndSupportsPluginFiltering() throws Exception {
        Path file = temporary.resolve("logs.db");
        DatabaseManager database = new DatabaseManager(file.toString());
        try {
            LogService service = new LogService(new LogRepository(database));
            service.recordSystem("core.action", "target", "detail", null, null, null);
            service.recordPlugin("sample", "plugin.action", "plugin-target", "plugin-detail", null, null, "request-1");

            LogService.Page defaultPage = service.list(null, 1, 50);
            assertEquals(1, defaultPage.total());
            assertEquals("CORE", defaultPage.logs().getFirst().getSourceType());
            assertNull(defaultPage.logs().getFirst().getPluginName());

            LogRepository.Query plugin = new LogRepository.Query(null, "PLUGIN", "sample", null,
                    null, null, null, "request-1", null, null);
            assertEquals(1, service.list(plugin, 1, 50).total());
            assertEquals(2, service.list(new LogRepository.Query(null, "ALL", null, null,
                    null, null, null, null, null, null), 1, 50).total());
            assertEquals(0, service.list(new LogRepository.Query("%", "ALL", null, null,
                    null, null, null, null, null, null), 1, 50).total());
        } finally {
            database.close();
        }
    }

    @Test
    void aggregatesExcessPluginLogs() throws Exception {
        DatabaseManager database = new DatabaseManager(temporary.resolve("suppression.db").toString());
        try {
            LogService service = new LogService(new LogRepository(database));
            for (int i = 0; i < LogService.PLUGIN_LOGS_PER_MINUTE + 5; i++) {
                service.recordPlugin("noisy", "tick", "target", "detail", null, null, null);
            }
            LogRepository.Query plugin = new LogRepository.Query(null, "PLUGIN", "noisy", null,
                    null, null, null, null, null, null);
            LogService.Page page = service.list(plugin, 1, 500);
            assertEquals(LogService.PLUGIN_LOGS_PER_MINUTE + 1, page.total());
            assertEquals(1, page.logs().stream().filter(log -> "log.suppressed".equals(log.getAction())).count());
        } finally {
            database.close();
        }
    }
}
