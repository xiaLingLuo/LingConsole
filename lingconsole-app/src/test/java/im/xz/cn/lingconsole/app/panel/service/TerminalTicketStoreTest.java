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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TerminalTicketStoreTest {

    @Test
    void ticketIsSingleUseAndBoundToUser() {
        TerminalTicketStore store = new TerminalTicketStore();
        String ticket = store.issue("user-a", "node1", "appX");

        TerminalTicketStore.Ticket first = store.consume(ticket, "user-a");
        assertEquals("node1", first.nodeId());
        assertEquals("appX", first.appId());

        assertNull(store.consume(ticket, "user-a"), "票据只能消费一次");
    }

    @Test
    void ticketRejectsOtherUser() {
        TerminalTicketStore store = new TerminalTicketStore();
        String ticket = store.issue("user-a", "node1", "");

        assertNull(store.consume(ticket, "user-b"), "其他用户不得消费票据");
        assertNull(store.consume(null, "user-a"), "空票据应拒绝");
    }

    @Test
    void ticketExpires() {
        TerminalTicketStore store = new TerminalTicketStore(-1);
        String ticket = store.issue("user-a", "node1", "");
        assertNull(store.consume(ticket, "user-a"), "过期票据应被拒绝");
        store.cleanup();
        assertEquals(0, store.size());
        store.close();
    }

    @Test
    void concurrentConsumptionHasSingleWinner() throws Exception {
        try (TerminalTicketStore store = new TerminalTicketStore()) {
            String ticket = store.issue("user-a", "node1", "");
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
            try {
                java.util.List<java.util.concurrent.Future<TerminalTicketStore.Ticket>> results =
                        new java.util.ArrayList<>();
                for (int i = 0; i < 32; i++) {
                    results.add(pool.submit(() -> store.consume(ticket, "user-a")));
                }
                assertEquals(1, results.stream().filter(result -> {
                    try { return result.get() != null; } catch (Exception e) { throw new RuntimeException(e); }
                }).count());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void enforcesGlobalUserAndIpLimits() {
        try (TerminalTicketStore userLimited = new TerminalTicketStore(60_000, 3, 1, 10)) {
            userLimited.issue("user-a", "node1", "", "ip-a");
            assertThrows(TerminalTicketStore.LimitExceededException.class,
                    () -> userLimited.issue("user-a", "node1", "", "ip-a"));
        }
        try (TerminalTicketStore ipLimited = new TerminalTicketStore(60_000, 3, 3, 1)) {
            ipLimited.issue("user-a", "node1", "", "ip-a");
            assertThrows(TerminalTicketStore.LimitExceededException.class,
                    () -> ipLimited.issue("user-b", "node1", "", "ip-a"));
        }
        try (TerminalTicketStore globalLimited = new TerminalTicketStore(60_000, 1, 1, 10)) {
            globalLimited.issue("user-a", "node1", "", "ip-a");
            assertThrows(TerminalTicketStore.LimitExceededException.class,
                    () -> globalLimited.issue("user-b", "node1", "", "ip-b"));
        }
    }
}
