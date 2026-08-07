/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package im.xz.cn.lingconsole.app.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import im.xz.cn.lingconsole.app.panel.exception.ApiException;
import im.xz.cn.lingconsole.app.panel.repository.DatabaseManager;
import im.xz.cn.lingconsole.app.panel.repository.NodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodePermissionIndexOwnershipTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void synchronizedNodeRemainsUsableWhenAnotherNodeIsOffline() throws Exception {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("panel.db").toString());
        try {
            NodeRepository nodes = new NodeRepository(database);
            NodePermissionIndex index = new NodePermissionIndex(nodes, new NodeService(nodes));
            try {
                index.synchronizeSnapshot("node-a", MAPPER.readTree("[{\"id\":\"existing\",\"name\":\"Existing\"}]"));

                assertTrue(index.ownsApp("node-a", "existing"));
                assertDoesNotThrow(() -> index.requireOwnedApp("node-a", "existing"));
                assertTrue(index.reserveAppId("newapp", "node-a"));
                index.releaseReservation("newapp", "node-a");

                assertFalse(index.ownsApp("node-b", "existing"));
                assertThrows(ApiException.class, () -> index.requireOwnedApp("node-b", "existing"));
                assertThrows(ApiException.class, () -> index.reserveAppId("other", "node-b"));
            } finally {
                index.stop();
            }
        } finally {
            database.close();
        }
    }
}
