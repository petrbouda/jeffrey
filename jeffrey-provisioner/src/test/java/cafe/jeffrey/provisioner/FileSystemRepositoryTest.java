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

package cafe.jeffrey.provisioner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.type.TypeReference;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.repository.RemoteProject;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The marker files are the hub's only record of what the provisioner declared, so each one must
 * land whole and must be the only thing a declaration leaves behind.
 */
class FileSystemRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-02-20T15:30:45.123Z");
    private static final String MACHINE_ZONE = "Europe/Prague";
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneId.of(MACHINE_ZONE));

    private static final String WORKSPACE_REF_ID = "ws-001";
    private static final String PROJECT_ID = "proj-001";
    private static final String PROJECT_NAME = "project-alpha";
    private static final String INSTANCE_ID = "inst-001";
    private static final String SESSION_ID = "session-001";

    /*
     * What each marker carries: its own id and what the hub reads. Where an entity lies is never
     * written down — the hub reads it from the directory the marker sits in.
     */
    private static final Set<String> PROJECT_INFO_FIELDS =
            Set.of("projectId", "projectName", "createdAt", "attributes");
    private static final Set<String> INSTANCE_INFO_FIELDS = Set.of("instanceId", "createdAt");
    private static final Set<String> SESSION_INFO_FIELDS = Set.of("sessionId", "createdAt", "zoneId");

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final FileSystemRepository repository = new FileSystemRepository(FIXED_CLOCK);

    @TempDir
    Path tempDir;

    private Path workspacePath() throws IOException {
        return Files.createDirectories(tempDir.resolve("workspaces").resolve(WORKSPACE_REF_ID));
    }

    private static Path createProjectDir(Path workspacePath) throws IOException {
        return Files.createDirectories(workspacePath.resolve(PROJECT_NAME));
    }

    private static Set<String> fieldsOf(Path marker) throws IOException {
        return Json.read(Files.readString(marker), JSON_OBJECT).keySet();
    }

    @Nested
    class Markers {

        @Test
        void addProject_writesTheProjectMarker() throws IOException {
            Path projectPath = createProjectDir(workspacePath());

            repository.addProject(PROJECT_ID, PROJECT_NAME, Map.of("cluster", "blue"), projectPath);

            RemoteProject written = Json.read(
                    Files.readString(projectPath.resolve(JeffreyLayout.PROJECT_INFO_FILE)), RemoteProject.class);
            assertEquals(new RemoteProject(PROJECT_ID, PROJECT_NAME, NOW.toEpochMilli(), Map.of("cluster", "blue")),
                    written);
        }

        @Test
        void addProject_writesOnlyItsIdAndTheFieldsTheHubReads() throws IOException {
            Path projectPath = createProjectDir(workspacePath());

            repository.addProject(PROJECT_ID, PROJECT_NAME, Map.of(), projectPath);

            assertEquals(PROJECT_INFO_FIELDS, fieldsOf(projectPath.resolve(JeffreyLayout.PROJECT_INFO_FILE)));
        }

        @Test
        void addInstance_writesOnlyItsIdAndTheFieldsTheHubReads() throws IOException {
            Path instancePath = Files.createDirectories(workspacePath().resolve(PROJECT_NAME).resolve(INSTANCE_ID));

            repository.addInstance(INSTANCE_ID, instancePath);

            assertEquals(INSTANCE_INFO_FIELDS, fieldsOf(instancePath.resolve(JeffreyLayout.INSTANCE_INFO_FILE)));
        }

        @Test
        void addSession_writesOnlyItsIdAndTheFieldsTheHubReads() throws IOException {
            Path sessionPath = Files.createDirectories(
                    workspacePath().resolve(PROJECT_NAME).resolve(INSTANCE_ID).resolve(SESSION_ID));

            repository.addSession(SESSION_ID, sessionPath);

            assertEquals(SESSION_INFO_FIELDS, fieldsOf(sessionPath.resolve(JeffreyLayout.SESSION_INFO_FILE)));
        }

        /**
         * async-profiler writes the {@code %t} in a recording's name in the machine's local time, so
         * the session says which zone that is; the hub reads the name back in it.
         */
        @Test
        void addSession_recordsTheMachinesTimeZone() throws IOException {
            Path sessionPath = Files.createDirectories(
                    workspacePath().resolve(PROJECT_NAME).resolve(INSTANCE_ID).resolve(SESSION_ID));

            repository.addSession(SESSION_ID, sessionPath);

            RemoteProjectInstanceSession marker = Json.read(
                    Files.readString(sessionPath.resolve(JeffreyLayout.SESSION_INFO_FILE)),
                    RemoteProjectInstanceSession.class);
            assertEquals(MACHINE_ZONE, marker.zoneId());
            assertEquals(NOW.toEpochMilli(), marker.createdAt());
        }

        /**
         * The hub lists whatever the volume holds, so a scratch file left behind by the atomic
         * write, or an index beside the markers, would be read as part of the repository.
         */
        @Test
        void aDeclarationLeavesNothingButItsMarkers() throws IOException {
            Path workspacePath = workspacePath();
            Path projectPath = createProjectDir(workspacePath);
            Path instancePath = Files.createDirectories(projectPath.resolve(INSTANCE_ID));
            Path sessionPath = Files.createDirectories(instancePath.resolve(SESSION_ID));

            repository.addProject(PROJECT_ID, PROJECT_NAME, Map.of(), projectPath);
            repository.addInstance(INSTANCE_ID, instancePath);
            repository.addSession(SESSION_ID, sessionPath);

            try (Stream<Path> files = Files.walk(workspacePath)) {
                Set<Path> written = files.filter(Files::isRegularFile)
                        .map(workspacePath::relativize)
                        .collect(Collectors.toSet());
                assertEquals(Set.of(
                        Path.of(PROJECT_NAME, JeffreyLayout.PROJECT_INFO_FILE),
                        Path.of(PROJECT_NAME, INSTANCE_ID, JeffreyLayout.INSTANCE_INFO_FILE),
                        Path.of(PROJECT_NAME, INSTANCE_ID, SESSION_ID, JeffreyLayout.SESSION_INFO_FILE)), written);
            }
        }

        @Test
        void aFailedWriteThrows() throws IOException {
            // The project directory does not exist, so the marker write fails
            Path missingProjectPath = workspacePath().resolve("never-created");

            assertThrows(RuntimeException.class, () ->
                    repository.addProject(PROJECT_ID, PROJECT_NAME, Map.of(), missingProjectPath));
        }
    }
}
