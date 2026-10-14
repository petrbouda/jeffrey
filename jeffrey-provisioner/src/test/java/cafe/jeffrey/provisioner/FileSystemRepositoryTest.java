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
import cafe.jeffrey.shared.common.model.RepositoryType;
import cafe.jeffrey.shared.common.model.repository.RemoteProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String WORKSPACE_REF_ID = "ws-001";
    private static final String PROJECT_ID = "proj-001";
    private static final String PROJECT_NAME = "project-alpha";
    private static final String INSTANCE_ID = "inst-001";
    private static final String SESSION_ID = "session-001";

    /** What the hub reads to list a session, plus the order the provisioner reads back. */
    private static final Set<String> SESSION_INFO_FIELDS = Set.of(
            "sessionId", "instanceId", "createdAt", "order", "relativeSessionPath");

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

    @Nested
    class Markers {

        @Test
        void addProject_writesTheProjectMarker() throws IOException {
            Path projectPath = createProjectDir(workspacePath());

            repository.addProject(PROJECT_ID, PROJECT_NAME, WORKSPACE_REF_ID,
                    "/workspaces", RepositoryType.ASYNC_PROFILER, Map.of(), projectPath);

            RemoteProject written = Json.read(
                    Files.readString(projectPath.resolve(JeffreyLayout.PROJECT_INFO_FILE)), RemoteProject.class);
            assertEquals(PROJECT_ID, written.projectId());
            assertEquals(NOW.toEpochMilli(), written.createdAt());
        }

        @Test
        void addSession_writesOnlyTheFieldsItsReadersUse() throws IOException {
            Path sessionPath = Files.createDirectories(
                    workspacePath().resolve(PROJECT_NAME).resolve(INSTANCE_ID).resolve(SESSION_ID));

            repository.addSession(SESSION_ID, INSTANCE_ID, 1, sessionPath);

            Map<String, Object> written = Json.read(
                    Files.readString(sessionPath.resolve(JeffreyLayout.SESSION_INFO_FILE)), JSON_OBJECT);
            assertEquals(SESSION_INFO_FIELDS, written.keySet());
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

            repository.addProject(PROJECT_ID, PROJECT_NAME, WORKSPACE_REF_ID,
                    "/workspaces", RepositoryType.ASYNC_PROFILER, Map.of(), projectPath);
            repository.addInstance(INSTANCE_ID, PROJECT_ID, WORKSPACE_REF_ID, instancePath);
            repository.addSession(SESSION_ID, INSTANCE_ID, 1, sessionPath);

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
                    repository.addProject(PROJECT_ID, PROJECT_NAME, WORKSPACE_REF_ID,
                            "/workspaces", RepositoryType.ASYNC_PROFILER, Map.of(), missingProjectPath));
        }
    }
}
