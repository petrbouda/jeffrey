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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every declaration the provisioner writes is announced in the workspace's pending index, so
 * the hub never has to walk the tree to find it. The marker is always written first — a hint
 * that outran its declaration would be read, found unusable, and retried.
 */
class FileSystemRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-02-20T15:30:45.123Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String WORKSPACE_REF_ID = "ws-001";
    private static final String PROJECT_ID = "proj-001";
    private static final String PROJECT_NAME = "project-alpha";
    private static final String INSTANCE_ID = "inst-001";
    private static final String SESSION_ID = "session-001";

    /** What the hub reads to materialize a session, plus the order the provisioner reads back. */
    private static final Set<String> SESSION_INFO_FIELDS = Set.of(
            "sessionId", "instanceId", "createdAt", "order", "relativeSessionPath", "heartbeatExpected");

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    @TempDir
    Path tempDir;

    private Path workspacePath() throws IOException {
        return Files.createDirectories(tempDir.resolve("workspaces").resolve(WORKSPACE_REF_ID));
    }

    private static List<Path> pendingEntries(Path workspacePath) throws IOException {
        Path pendingDir = workspacePath.resolve(JeffreyLayout.PENDING_DIR);
        if (!Files.isDirectory(pendingDir)) {
            return List.of();
        }
        try (var files = Files.list(pendingDir)) {
            return files.sorted().toList();
        }
    }

    private static Path createProjectDir(Path workspacePath) throws IOException {
        return Files.createDirectories(workspacePath.resolve(PROJECT_NAME));
    }

    @Nested
    class Announcements {

        @Test
        void addProject_announcesTheProjectDirectory() throws IOException {
            Path workspacePath = workspacePath();
            Path projectPath = createProjectDir(workspacePath);
            var repository = new FileSystemRepository(FIXED_CLOCK, workspacePath);

            repository.addProject(PROJECT_ID, PROJECT_NAME, "Alpha", WORKSPACE_REF_ID,
                    "/workspaces", RepositoryType.ASYNC_PROFILER, Map.of(), projectPath);

            List<Path> entries = pendingEntries(workspacePath);
            assertEquals(1, entries.size());
            assertEquals(PROJECT_NAME, Files.readString(entries.getFirst()));
            assertTrue(entries.getFirst().getFileName().toString().endsWith("_" + PROJECT_ID));
        }

        @Test
        void addInstance_announcesThePathRelativeToTheWorkspace() throws IOException {
            Path workspacePath = workspacePath();
            Path instancePath = Files.createDirectories(
                    workspacePath.resolve(PROJECT_NAME).resolve(INSTANCE_ID));
            var repository = new FileSystemRepository(FIXED_CLOCK, workspacePath);

            repository.addInstance(INSTANCE_ID, PROJECT_ID, WORKSPACE_REF_ID, instancePath);

            List<Path> entries = pendingEntries(workspacePath);
            assertEquals(1, entries.size());
            assertEquals(Path.of(PROJECT_NAME, INSTANCE_ID).toString(),
                    Files.readString(entries.getFirst()));
        }

        @Test
        void addSession_writesOnlyTheFieldsItsReadersUse() throws IOException {
            Path workspacePath = workspacePath();
            Path sessionPath = Files.createDirectories(
                    workspacePath.resolve(PROJECT_NAME).resolve(INSTANCE_ID).resolve(SESSION_ID));
            var repository = new FileSystemRepository(FIXED_CLOCK, workspacePath);

            repository.addSession(SESSION_ID, INSTANCE_ID, 1, sessionPath, true);

            Map<String, Object> written = Json.read(
                    Files.readString(sessionPath.resolve(JeffreyLayout.SESSION_INFO_FILE)), JSON_OBJECT);
            assertEquals(SESSION_INFO_FIELDS, written.keySet());
        }

        @Test
        void addSession_announcesThePathRelativeToTheWorkspace() throws IOException {
            Path workspacePath = workspacePath();
            Path sessionPath = Files.createDirectories(
                    workspacePath.resolve(PROJECT_NAME).resolve(INSTANCE_ID).resolve(SESSION_ID));
            var repository = new FileSystemRepository(FIXED_CLOCK, workspacePath);

            repository.addSession(SESSION_ID, INSTANCE_ID, 1, sessionPath, true);

            List<Path> entries = pendingEntries(workspacePath);
            assertEquals(1, entries.size());
            assertEquals(Path.of(PROJECT_NAME, INSTANCE_ID, SESSION_ID).toString(),
                    Files.readString(entries.getFirst()));
        }

        @Test
        void everyDeclarationGetsExactlyOneEntry() throws IOException {
            Path workspacePath = workspacePath();
            Path projectPath = createProjectDir(workspacePath);
            Path instancePath = Files.createDirectories(projectPath.resolve(INSTANCE_ID));
            Path sessionPath = Files.createDirectories(instancePath.resolve(SESSION_ID));
            var repository = new FileSystemRepository(FIXED_CLOCK, workspacePath);

            repository.addProject(PROJECT_ID, PROJECT_NAME, "Alpha", WORKSPACE_REF_ID,
                    "/workspaces", RepositoryType.ASYNC_PROFILER, Map.of(), projectPath);
            repository.addInstance(INSTANCE_ID, PROJECT_ID, WORKSPACE_REF_ID, instancePath);
            repository.addSession(SESSION_ID, INSTANCE_ID, 1, sessionPath, true);

            assertEquals(3, pendingEntries(workspacePath).size());
        }
    }

    @Nested
    class WriteOrdering {

        /**
         * The hint must never be readable before the declaration it points at, otherwise the hub
         * would consume a hint for a subtree it cannot materialize yet.
         */
        @Test
        void markerExistsWheneverItsHintDoes() throws IOException {
            Path workspacePath = workspacePath();
            Path projectPath = createProjectDir(workspacePath);
            var repository = new FileSystemRepository(FIXED_CLOCK, workspacePath);

            repository.addProject(PROJECT_ID, PROJECT_NAME, "Alpha", WORKSPACE_REF_ID,
                    "/workspaces", RepositoryType.ASYNC_PROFILER, Map.of(), projectPath);

            String announced = Files.readString(pendingEntries(workspacePath).getFirst());
            Path declared = workspacePath.resolve(announced);
            assertTrue(Files.isRegularFile(declared.resolve(JeffreyLayout.PROJECT_INFO_FILE)));
        }

        @Test
        void noEntryIsWrittenWhenTheMarkerWriteFails() throws IOException {
            Path workspacePath = workspacePath();
            // The project directory does not exist, so the marker write fails
            Path missingProjectPath = workspacePath.resolve("never-created");
            var repository = new FileSystemRepository(FIXED_CLOCK, workspacePath);

            assertThrows(RuntimeException.class, () ->
                    repository.addProject(PROJECT_ID, PROJECT_NAME, "Alpha", WORKSPACE_REF_ID,
                            "/workspaces", RepositoryType.ASYNC_PROFILER, Map.of(), missingProjectPath));

            assertTrue(pendingEntries(workspacePath).isEmpty());
        }
    }
}
