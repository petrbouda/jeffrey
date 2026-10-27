/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.manager.project;

import cafe.jeffrey.hub.core.volume.TestVolume;
import cafe.jeffrey.hub.core.volume.WorkspaceDirectory;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A workspace's projects are its directories with a {@code .project-info.json}, known by the
 * provisioner's project id from that marker.
 */
class HubProjectsManagerTest {

    private static final Instant T0 = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant NOW = T0.plusSeconds(3600);

    @TempDir
    Path root;

    private Path workspaceDir;

    private HubProjectsManager projects;

    @BeforeEach
    void setUp() {
        workspaceDir = TestVolume.workspace(root, "prod-ws", "Production", T0);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        projects = new HubProjectsManager(new WorkspaceDirectory(workspaceDir, "Production", T0),
                project -> new HubProjectManager(project, TestVolume.liveness(clock)));
    }

    @Test
    void readsEachProjectFromItsMarker() {
        TestVolume.project(workspaceDir, "p-1", "orders", T0);

        assertEquals(List.of(new ProjectInfo("p-1", "orders", "prod-ws", T0, Map.of("env", "test"))),
                projects.findAll().stream().map(ProjectManager::info).toList());
    }

    @Test
    void findsAProjectByTheProvisionersId() {
        TestVolume.project(workspaceDir, "p-1", "orders", T0);
        TestVolume.project(workspaceDir, "p-2", "payments", T0);

        assertEquals("payments", projects.project("p-2").orElseThrow().info().name());
        assertTrue(projects.project("orders").isEmpty(), "the directory name is not the id");
    }

    @Test
    void aDirectoryWithoutAReadableMarkerIsNoProject() throws IOException {
        Files.createDirectories(workspaceDir.resolve("empty"));
        Path broken = Files.createDirectories(workspaceDir.resolve("broken"));
        Files.writeString(broken.resolve(".project-info.json"), "{ not json");

        assertTrue(projects.findAll().isEmpty());
    }

    @Test
    void detailCountsTheSessionsAndSaysWhetherOneRecords() {
        Path project = TestVolume.project(workspaceDir, "p-1", "orders", T0);
        assertNull(projects.project("p-1").orElseThrow().detailedInfo().status(), "no session yet");

        Path instance = TestVolume.instance(project, "pod-a", T0);
        TestVolume.finished(TestVolume.session(instance, "s-1", T0), T0.plusSeconds(60));
        TestVolume.heartbeat(TestVolume.session(instance, "s-2", T0.plusSeconds(120)), NOW);

        ProjectManager.DetailedProjectInfo detail = projects.project("p-1").orElseThrow().detailedInfo();
        assertEquals(RecordingStatus.ACTIVE, detail.status());
        assertEquals(2, detail.sessionCount());
    }

    @Test
    void deleteTakesTheProjectDirectory() {
        Path project = TestVolume.project(workspaceDir, "p-1", "orders", T0);

        projects.project("p-1").orElseThrow().delete();

        assertFalse(Files.exists(project));
    }
}
