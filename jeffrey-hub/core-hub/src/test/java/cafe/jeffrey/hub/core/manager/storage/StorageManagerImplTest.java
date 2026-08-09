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

package cafe.jeffrey.hub.core.manager.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.FileTypeUsage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.ProjectStorage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.StoredFile;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageManagerImplTest {

    private static final Instant CREATED_AT = Instant.parse("2026-04-01T10:00:00Z");
    private static final Instant LAST_ACTIVITY = Instant.parse("2026-04-02T15:30:00Z");

    @TempDir
    Path homeDir;

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    WorkspaceManager workspaceManager;

    @Mock
    ProjectsManager projectsManager;

    @Mock
    ProjectManager projectManager;

    @Mock
    RepositoryManager repositoryManager;

    StorageManagerImpl storageManager;

    @BeforeEach
    void setUp() {
        storageManager = new StorageManagerImpl(workspacesManager, new HubJeffreyDirs(homeDir));
    }

    @Nested
    class InfrastructureSizes {

        @Test
        void measuresDatabaseQueueAndTempDirectories() throws IOException {
            Files.write(homeDir.resolve("jeffrey-data.db"), new byte[100]);
            Files.write(homeDir.resolve("jeffrey-data.db.wal"), new byte[20]);
            Path events = Files.createDirectories(homeDir.resolve("workspaces").resolve(".events"));
            Files.write(events.resolve("event.json"), new byte[30]);
            Path temp = Files.createDirectories(homeDir.resolve("tmp"));
            Files.write(temp.resolve("scratch.bin"), new byte[40]);
            when(workspacesManager.findAll()).thenReturn(List.of());

            StorageOverview overview = storageManager.overview();

            assertThat(overview.infrastructure().databaseBytes()).isEqualTo(120L);
            assertThat(overview.infrastructure().queueBytes()).isEqualTo(30L);
            assertThat(overview.infrastructure().tempBytes()).isEqualTo(40L);
        }

        @Test
        void reportsZeroesWhenNothingExistsOnDisk() {
            when(workspacesManager.findAll()).thenReturn(List.of());

            StorageOverview overview = storageManager.overview();

            assertThat(overview.infrastructure().databaseBytes()).isZero();
            assertThat(overview.infrastructure().queueBytes()).isZero();
            assertThat(overview.infrastructure().tempBytes()).isZero();
            assertThat(overview.projects()).isEmpty();
        }

        @Test
        void resolvesDiskSpaceOfTheHomeDirectoryVolume() {
            when(workspacesManager.findAll()).thenReturn(List.of());

            StorageOverview overview = storageManager.overview();

            assertThat(overview.disk().totalBytes()).isPositive();
        }
    }

    @Nested
    class ProjectAggregation {

        @Test
        void collectsPerFileTypeStatisticsAcrossSessions() {
            mockSingleProject(List.of(
                    session("session-1", List.of(
                            file("recording-1.jfr", SupportedRecordingFile.JFR, 700L, CREATED_AT),
                            file("recording-2.jfr.lz4", SupportedRecordingFile.JFR_LZ4, 300L, CREATED_AT),
                            file("heapdump.hprof.gz", SupportedRecordingFile.HEAP_DUMP_GZ, 200L, CREATED_AT))),
                    session("session-2", List.of(
                            file("recording-3.jfr", SupportedRecordingFile.JFR, 500L, LAST_ACTIVITY),
                            file("service-jvm.log", SupportedRecordingFile.JVM_LOG, 40L, CREATED_AT),
                            file("service-app.log", SupportedRecordingFile.APP_LOG, 30L, CREATED_AT),
                            file("cpu.pprof", SupportedRecordingFile.PPROF, 10L, CREATED_AT)))));

            StorageOverview overview = storageManager.overview();

            assertThat(overview.projects()).hasSize(1);
            ProjectStorage project = overview.projects().getFirst();
            assertThat(project.workspaceId()).isEqualTo("ws-1");
            assertThat(project.workspaceName()).isEqualTo("production");
            assertThat(project.projectId()).isEqualTo("prj-1");
            assertThat(project.projectName()).isEqualTo("order-service");
            assertThat(project.projectLabel()).isEqualTo("Order Service");
            assertThat(project.totalSizeBytes()).isEqualTo(1780L);
            assertThat(project.totalFiles()).isEqualTo(7);
            assertThat(project.lastActivityTimeMillis()).isEqualTo(LAST_ACTIVITY.toEpochMilli());

            // Sorted by size, JFR aggregated across both sessions
            assertThat(project.fileTypes()).containsExactly(
                    new FileTypeUsage(SupportedRecordingFile.JFR, 1200L, 2),
                    new FileTypeUsage(SupportedRecordingFile.JFR_LZ4, 300L, 1),
                    new FileTypeUsage(SupportedRecordingFile.HEAP_DUMP_GZ, 200L, 1),
                    new FileTypeUsage(SupportedRecordingFile.JVM_LOG, 40L, 1),
                    new FileTypeUsage(SupportedRecordingFile.APP_LOG, 30L, 1),
                    new FileTypeUsage(SupportedRecordingFile.PPROF, 10L, 1));

            assertThat(project.largestFiles()).containsExactly(
                    new StoredFile("recording-1.jfr", 700L),
                    new StoredFile("recording-3.jfr", 500L),
                    new StoredFile("recording-2.jfr.lz4", 300L));
        }

        @Test
        void reportsEmptyBreakdownForProjectWithoutSessions() {
            mockSingleProject(List.of());

            StorageOverview overview = storageManager.overview();

            assertThat(overview.projects()).hasSize(1);
            ProjectStorage project = overview.projects().getFirst();
            assertThat(project.totalSizeBytes()).isZero();
            assertThat(project.totalFiles()).isZero();
            assertThat(project.lastActivityTimeMillis()).isZero();
            assertThat(project.fileTypes()).isEmpty();
            assertThat(project.largestFiles()).isEmpty();
        }

        @Test
        void countsFilesWithUnknownSizeAsZeroBytes() {
            mockSingleProject(List.of(
                    session("session-1", List.of(
                            file("recording-1.jfr", SupportedRecordingFile.JFR, 700L, CREATED_AT),
                            file("recording-2.jfr", SupportedRecordingFile.JFR, null, CREATED_AT)))));

            StorageOverview overview = storageManager.overview();

            ProjectStorage project = overview.projects().getFirst();
            assertThat(project.totalSizeBytes()).isEqualTo(700L);
            assertThat(project.totalFiles()).isEqualTo(2);
            assertThat(project.fileTypes()).containsExactly(
                    new FileTypeUsage(SupportedRecordingFile.JFR, 700L, 2));
        }

        private void mockSingleProject(List<RecordingSession> sessions) {
            WorkspaceInfo workspaceInfo = new WorkspaceInfo(
                    "ws-1", "ws-1", "repo-1", "production",
                    null, null, CREATED_AT, WorkspaceStatus.AVAILABLE, 1);
            ProjectInfo projectInfo = new ProjectInfo(
                    "prj-1", "origin-1", "order-service", "Order Service", "default",
                    "ws-1", CREATED_AT, CREATED_AT, Map.of(), null);

            doReturn(List.of(workspaceManager)).when(workspacesManager).findAll();
            when(workspaceManager.localInfo()).thenReturn(workspaceInfo);
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);
            doReturn(List.of(projectManager)).when(projectsManager).findAll();
            when(projectManager.info()).thenReturn(projectInfo);
            when(projectManager.repositoryManager()).thenReturn(repositoryManager);
            when(repositoryManager.listRecordingSessions(true)).thenReturn(sessions);
        }

        private RecordingSession session(String id, List<RepositoryFile> files) {
            return new RecordingSession(
                    id, id, "instance-1", CREATED_AT, null,
                    RecordingStatus.FINISHED, null, null, files, false);
        }

        private RepositoryFile file(String name, SupportedRecordingFile fileType, Long size, Instant createdAt) {
            return new RepositoryFile(name, name, createdAt, size, fileType, RecordingStatus.FINISHED, null);
        }
    }
}
