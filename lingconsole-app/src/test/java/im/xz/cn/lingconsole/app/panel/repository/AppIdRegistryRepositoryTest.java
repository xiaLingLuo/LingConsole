/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package im.xz.cn.lingconsole.app.panel.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppIdRegistryRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void concurrentReservationsHaveExactlyOneWinner() throws Exception {
        try (TestDatabase database = database()) {
            AppIdRegistryRepository registry = new AppIdRegistryRepository(database.manager());
            try (var executor = Executors.newFixedThreadPool(8)) {
                var tasks = java.util.stream.IntStream.range(0, 24)
                        .<Callable<Boolean>>mapToObj(i -> () -> registry.reserve("shared", "node-" + i))
                        .toList();
                long winners = executor.invokeAll(tasks).stream().filter(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).count();
                assertEquals(1, winners);
            }
        }
    }

    @Test
    void synchronizationConflictFailsClosedForBothNodes() throws Exception {
        try (TestDatabase database = database()) {
            AppIdRegistryRepository registry = new AppIdRegistryRepository(database.manager());
            registry.synchronize("node-a", Set.of("same"));
            assertTrue(registry.owns("same", "node-a"));

            registry.synchronize("node-b", Set.of("same"));

            assertFalse(registry.owns("same", "node-a"));
            assertFalse(registry.owns("same", "node-b"));
        }
    }

    @Test
    void failedCreateReservationCanBeReleasedAndReused() throws Exception {
        try (TestDatabase database = database()) {
            AppIdRegistryRepository registry = new AppIdRegistryRepository(database.manager());
            assertTrue(registry.reserve("retry", "node-a"));
            registry.releaseReservation("retry", "node-a");
            assertTrue(registry.reserve("retry", "node-b"));
            assertTrue(registry.activate("retry", "node-b"));
            assertTrue(registry.owns("retry", "node-b"));
            registry.releaseOwned("retry", "node-b");
            assertFalse(registry.owns("retry", "node-b"));
        }
    }

    @Test
    void startupSynchronizationRecoversCompletedCreateReservation() throws Exception {
        try (TestDatabase database = database()) {
            AppIdRegistryRepository registry = new AppIdRegistryRepository(database.manager());
            assertTrue(registry.reserve("created", "node-a"));

            registry.synchronize("node-a", Set.of("created"));

            assertTrue(registry.owns("created", "node-a"));
            assertTrue(registry.activate("created", "node-a"));
        }
    }

    @Test
    void incompleteSynchronizationDoesNotReleaseActiveOwnership() throws Exception {
        try (TestDatabase database = database()) {
            AppIdRegistryRepository registry = new AppIdRegistryRepository(database.manager());
            registry.synchronize("node-a", Set.of("kept"));

            registry.synchronize("node-a", Set.of());

            assertTrue(registry.owns("kept", "node-a"));
            assertFalse(registry.reserve("kept", "node-b"));
        }
    }

    private TestDatabase database() throws Exception {
        String path = tempDir.resolve(java.util.UUID.randomUUID() + ".db").toString();
        return new TestDatabase(path, new DatabaseManager(path));
    }

    private record TestDatabase(String path, DatabaseManager manager) implements AutoCloseable {
        @Override
        public void close() {
            manager.close();
        }
    }
}
