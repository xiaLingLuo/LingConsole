/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package im.xz.cn.lingconsole.daemon.service;

import im.xz.cn.lingconsole.daemon.model.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TerminalServiceTest {

    @Test
    void appTerminalRemovesTheRegisteredListenerInstances() {
        ListenerTrackingAppProcess app = new ListenerTrackingAppProcess();
        TerminalService service = new TerminalService();

        TerminalService.TerminalSession terminal = service.createAppTerminal(app);
        terminal.close();

        assertEquals(1, app.outputRemovals);
        assertEquals(1, app.runningRemovals);
        assertSame(app.addedOutput, app.removedOutput);
        assertSame(app.addedRunning, app.removedRunning);
    }

    private static final class ListenerTrackingAppProcess extends AppProcess {
        private Consumer<String> addedOutput;
        private Consumer<String> removedOutput;
        private Consumer<Boolean> addedRunning;
        private Consumer<Boolean> removedRunning;
        private int outputRemovals;
        private int runningRemovals;

        private ListenerTrackingAppProcess() {
            super(testConfig(), new AppManager("."));
        }

        @Override
        public void addOutputListener(Consumer<String> listener) {
            addedOutput = listener;
        }

        @Override
        public void removeOutputListener(Consumer<String> listener) {
            removedOutput = listener;
            outputRemovals++;
        }

        @Override
        public void addRunningListener(Consumer<Boolean> listener) {
            addedRunning = listener;
        }

        @Override
        public void removeRunningListener(Consumer<Boolean> listener) {
            removedRunning = listener;
            runningRemovals++;
        }

        private static AppConfig testConfig() {
            AppConfig config = new AppConfig();
            config.setName("listener-test");
            return config;
        }
    }
}
