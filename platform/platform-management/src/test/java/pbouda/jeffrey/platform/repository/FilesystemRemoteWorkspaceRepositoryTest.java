/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.platform.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.repository.ProfilerSettings;
import pbouda.jeffrey.shared.common.model.repository.RemoteProject;
import pbouda.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import pbouda.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;
import pbouda.jeffrey.shared.common.model.repository.RemoteWorkspaceSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FilesystemRemoteWorkspaceRepositoryTest {

    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static Path writeProjectInfo(Path workspacePath, RemoteProject project) throws IOException {
        Path projectDir = Files.createDirectories(workspacePath.resolve(project.projectName()));
        Files.writeString(projectDir.resolve(".project-info.json"), Json.toString(project));
        return projectDir;
    }

    private static Path writeInstanceInfo(Path projectDir, RemoteProjectInstance instance) throws IOException {
        Path instanceDir = Files.createDirectories(projectDir.resolve(instance.instanceId()));
        Files.writeString(instanceDir.resolve(".instance-info.json"), Json.toString(instance));
        return instanceDir;
    }

    private static Path writeSessionInfo(Path instanceDir, RemoteProjectInstanceSession session) throws IOException {
        Path sessionDir = Files.createDirectories(instanceDir.resolve(session.sessionId()));
        Files.writeString(sessionDir.resolve(".session-info.json"), Json.toString(session));
        return sessionDir;
    }

    private static RemoteProject createProject(String id, String name) {
        return new RemoteProject(
                id, name, "Label " + name, "ws-001", 1718452800000L,
                "/workspaces", "ws-001", "ws-001/" + name,
                RepositoryType.ASYNC_PROFILER, Map.of("env", "test"));
    }

    private static RemoteProjectInstance createInstance(String instanceId, String projectId) {
        return new RemoteProjectInstance(
                instanceId, projectId, "ws-001", 1718452800000L,
                "ws-001/my-project/" + instanceId);
    }

    private static RemoteProjectInstanceSession createSession(
            String sessionId, String projectId, String instanceId) {
        return new RemoteProjectInstanceSession(
                sessionId, projectId, "ws-001", instanceId,
                1718452800000L, 1,
                "ws-001/my-project/" + instanceId + "/" + sessionId,
                "cpu=10ms");
    }

    @Nested
    class AllProjects {

        @Test
        void readsAllProjectInfoFiles(@TempDir Path workspacePath) throws IOException {
            RemoteProject project1 = createProject("proj-001", "project-alpha");
            RemoteProject project2 = createProject("proj-002", "project-beta");

            writeProjectInfo(workspacePath, project1);
            writeProjectInfo(workspacePath, project2);

            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, workspacePath);
            List<RemoteProject> projects = repo.allProjects();

            assertEquals(2, projects.size());

            List<String> projectIds = projects.stream().map(RemoteProject::projectId).sorted().toList();
            assertEquals(List.of("proj-001", "proj-002"), projectIds);

            RemoteProject read = projects.stream()
                    .filter(p -> p.projectId().equals("proj-001"))
                    .findFirst().orElseThrow();

            assertAll(
                    () -> assertEquals("project-alpha", read.projectName()),
                    () -> assertEquals("Label project-alpha", read.projectLabel()),
                    () -> assertEquals("ws-001", read.workspaceId()),
                    () -> assertEquals(1718452800000L, read.createdAt()),
                    () -> assertEquals(RepositoryType.ASYNC_PROFILER, read.repositoryType()),
                    () -> assertEquals(Map.of("env", "test"), read.attributes())
            );
        }

        @Test
        void skipsDirectoriesWithoutInfoFile(@TempDir Path workspacePath) throws IOException {
            RemoteProject project1 = createProject("proj-001", "project-alpha");
            writeProjectInfo(workspacePath, project1);

            // Directory without .project-info.json
            Files.createDirectories(workspacePath.resolve("orphan-dir"));

            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, workspacePath);
            List<RemoteProject> projects = repo.allProjects();

            assertEquals(1, projects.size());
            assertEquals("proj-001", projects.getFirst().projectId());
        }

        @Test
        void returnsEmptyList_whenWorkspaceEmpty(@TempDir Path workspacePath) {
            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, workspacePath);
            List<RemoteProject> projects = repo.allProjects();

            assertTrue(projects.isEmpty());
        }

        @Test
        void throwsException_whenWorkspacePathDoesNotExist(@TempDir Path tempDir) {
            Path nonExistent = tempDir.resolve("does-not-exist");
            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, nonExistent);

            assertThrows(IllegalArgumentException.class, repo::allProjects);
        }
    }

    @Nested
    class AllInstances {

        @Test
        void readsAllInstanceInfoFiles_forProject(@TempDir Path workspacePath) throws IOException {
            RemoteProject project = createProject("proj-001", "my-project");
            Path projectDir = writeProjectInfo(workspacePath, project);

            RemoteProjectInstance instance1 = createInstance("inst-001", "proj-001");
            RemoteProjectInstance instance2 = createInstance("inst-002", "proj-001");
            writeInstanceInfo(projectDir, instance1);
            writeInstanceInfo(projectDir, instance2);

            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, workspacePath);
            List<RemoteProjectInstance> instances = repo.allInstances(project);

            assertEquals(2, instances.size());

            List<String> instanceIds = instances.stream()
                    .map(RemoteProjectInstance::instanceId).sorted().toList();
            assertEquals(List.of("inst-001", "inst-002"), instanceIds);
        }

        @Test
        void skipsDirectoriesWithoutInstanceInfoFile(@TempDir Path workspacePath) throws IOException {
            RemoteProject project = createProject("proj-001", "my-project");
            Path projectDir = writeProjectInfo(workspacePath, project);

            RemoteProjectInstance instance1 = createInstance("inst-001", "proj-001");
            writeInstanceInfo(projectDir, instance1);

            // Directory without .instance-info.json (e.g., .settings)
            Files.createDirectories(projectDir.resolve("no-info-dir"));

            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, workspacePath);
            List<RemoteProjectInstance> instances = repo.allInstances(project);

            assertEquals(1, instances.size());
            assertEquals("inst-001", instances.getFirst().instanceId());
        }
    }

    @Nested
    class AllSessions {

        @Test
        void readsAllSessionInfoFiles_acrossInstances(@TempDir Path workspacePath) throws IOException {
            RemoteProject project = createProject("proj-001", "my-project");
            Path projectDir = writeProjectInfo(workspacePath, project);

            RemoteProjectInstance instance1 = createInstance("inst-001", "proj-001");
            RemoteProjectInstance instance2 = createInstance("inst-002", "proj-001");
            Path instanceDir1 = writeInstanceInfo(projectDir, instance1);
            Path instanceDir2 = writeInstanceInfo(projectDir, instance2);

            RemoteProjectInstanceSession session1 = createSession("sess-001", "proj-001", "inst-001");
            RemoteProjectInstanceSession session2 = createSession("sess-002", "proj-001", "inst-002");
            writeSessionInfo(instanceDir1, session1);
            writeSessionInfo(instanceDir2, session2);

            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, workspacePath);
            List<RemoteProjectInstanceSession> sessions = repo.allSessions(project);

            assertEquals(2, sessions.size());

            List<String> sessionIds = sessions.stream()
                    .map(RemoteProjectInstanceSession::sessionId).sorted().toList();
            assertEquals(List.of("sess-001", "sess-002"), sessionIds);
        }

        @Test
        void handlesMultipleSessionsPerInstance(@TempDir Path workspacePath) throws IOException {
            RemoteProject project = createProject("proj-001", "my-project");
            Path projectDir = writeProjectInfo(workspacePath, project);

            RemoteProjectInstance instance = createInstance("inst-001", "proj-001");
            Path instanceDir = writeInstanceInfo(projectDir, instance);

            RemoteProjectInstanceSession session1 = createSession("sess-001", "proj-001", "inst-001");
            RemoteProjectInstanceSession session2 = createSession("sess-002", "proj-001", "inst-001");
            RemoteProjectInstanceSession session3 = createSession("sess-003", "proj-001", "inst-001");
            writeSessionInfo(instanceDir, session1);
            writeSessionInfo(instanceDir, session2);
            writeSessionInfo(instanceDir, session3);

            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, workspacePath);
            List<RemoteProjectInstanceSession> sessions = repo.allSessions(project);

            assertEquals(3, sessions.size());
        }

        @Test
        void skipsDirectoriesWithoutSessionInfo(@TempDir Path workspacePath) throws IOException {
            RemoteProject project = createProject("proj-001", "my-project");
            Path projectDir = writeProjectInfo(workspacePath, project);

            RemoteProjectInstance instance = createInstance("inst-001", "proj-001");
            Path instanceDir = writeInstanceInfo(projectDir, instance);

            RemoteProjectInstanceSession session1 = createSession("sess-001", "proj-001", "inst-001");
            writeSessionInfo(instanceDir, session1);

            // Directory without .session-info.json
            Files.createDirectories(instanceDir.resolve("orphan-session-dir"));

            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, workspacePath);
            List<RemoteProjectInstanceSession> sessions = repo.allSessions(project);

            assertEquals(1, sessions.size());
            assertEquals("sess-001", sessions.getFirst().sessionId());
        }
    }

    @Nested
    class UploadSettings {

        @Test
        void writesNewSettingsFile(@TempDir Path workspacePath) throws IOException {
            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, workspacePath);
            var settings = new RemoteWorkspaceSettings(
                    new ProfilerSettings("cpu=10ms", Map.of("proj-1", "wall=5ms")));

            repo.uploadSettings(settings);

            Path settingsDir = workspacePath.resolve(".settings");
            assertTrue(Files.isDirectory(settingsDir));

            List<Path> settingsFiles;
            try (var stream = Files.list(settingsDir)) {
                settingsFiles = stream.filter(Files::isRegularFile).toList();
            }
            assertEquals(1, settingsFiles.size());

            String content = Files.readString(settingsFiles.getFirst());
            RemoteWorkspaceSettings read = Json.read(content, RemoteWorkspaceSettings.class);
            assertEquals(settings, read);
        }

        @Test
        void skipsUpload_whenIdenticalToLatest(@TempDir Path workspacePath) throws IOException {
            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, workspacePath);
            var settings = new RemoteWorkspaceSettings(
                    new ProfilerSettings("cpu=10ms", Map.of()));

            repo.uploadSettings(settings);

            // Second upload with identical settings
            repo.uploadSettings(settings);

            Path settingsDir = workspacePath.resolve(".settings");
            List<Path> settingsFiles;
            try (var stream = Files.list(settingsDir)) {
                settingsFiles = stream.filter(Files::isRegularFile).toList();
            }

            // Should still be just 1 file because identical settings are skipped
            assertEquals(1, settingsFiles.size());
        }

        @Test
        void createsNewVersion_whenDifferent(@TempDir Path workspacePath) throws IOException {
            var settings1 = new RemoteWorkspaceSettings(
                    new ProfilerSettings("cpu=10ms", Map.of()));
            var settings2 = new RemoteWorkspaceSettings(
                    new ProfilerSettings("wall=5ms", Map.of()));

            // Use different clock times to avoid filename collision
            Clock clock1 = Clock.fixed(Instant.parse("2025-06-15T12:00:00Z"), ZoneOffset.UTC);
            Clock clock2 = Clock.fixed(Instant.parse("2025-06-15T12:01:00Z"), ZoneOffset.UTC);

            var repo1 = new FilesystemRemoteWorkspaceRepository(clock1, workspacePath);
            repo1.uploadSettings(settings1);

            var repo2 = new FilesystemRemoteWorkspaceRepository(clock2, workspacePath);
            repo2.uploadSettings(settings2);

            Path settingsDir = workspacePath.resolve(".settings");
            List<Path> settingsFiles;
            try (var stream = Files.list(settingsDir)) {
                settingsFiles = stream.filter(Files::isRegularFile).toList();
            }

            assertEquals(2, settingsFiles.size());
        }
    }

    @Nested
    class RemoveLegacySettings {

        @Test
        void keepsOnlySpecifiedNumberOfVersions(@TempDir Path workspacePath) throws IOException {
            var settings1 = new RemoteWorkspaceSettings(new ProfilerSettings("v1", Map.of()));
            var settings2 = new RemoteWorkspaceSettings(new ProfilerSettings("v2", Map.of()));
            var settings3 = new RemoteWorkspaceSettings(new ProfilerSettings("v3", Map.of()));

            Clock clock1 = Clock.fixed(Instant.parse("2025-06-15T12:00:00Z"), ZoneOffset.UTC);
            Clock clock2 = Clock.fixed(Instant.parse("2025-06-15T12:01:00Z"), ZoneOffset.UTC);
            Clock clock3 = Clock.fixed(Instant.parse("2025-06-15T12:02:00Z"), ZoneOffset.UTC);

            new FilesystemRemoteWorkspaceRepository(clock1, workspacePath).uploadSettings(settings1);
            new FilesystemRemoteWorkspaceRepository(clock2, workspacePath).uploadSettings(settings2);
            new FilesystemRemoteWorkspaceRepository(clock3, workspacePath).uploadSettings(settings3);

            Path settingsDir = workspacePath.resolve(".settings");
            List<Path> beforeCleanup;
            try (var stream = Files.list(settingsDir)) {
                beforeCleanup = stream.filter(Files::isRegularFile).toList();
            }
            assertEquals(3, beforeCleanup.size());

            // Keep only 1 version (the newest)
            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, workspacePath);
            repo.removeLegacySettings(1);

            List<Path> afterCleanup;
            try (var stream = Files.list(settingsDir)) {
                afterCleanup = stream.filter(Files::isRegularFile).toList();
            }
            assertEquals(1, afterCleanup.size());
        }

        @Test
        void noOp_whenSettingsDirDoesNotExist(@TempDir Path workspacePath) {
            var repo = new FilesystemRemoteWorkspaceRepository(FIXED_CLOCK, workspacePath);

            // Should not throw
            assertDoesNotThrow(() -> repo.removeLegacySettings(2));
        }
    }
}
