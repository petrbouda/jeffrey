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
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.ProjectStorage;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics.FileTypeStats;
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
        void collectsPerProjectStatisticsAcrossWorkspaces() {
            WorkspaceInfo workspaceInfo = new WorkspaceInfo(
                    "ws-1", "ws-1", "repo-1", "production",
                    null, null, CREATED_AT, WorkspaceStatus.AVAILABLE, 1);
            ProjectInfo projectInfo = new ProjectInfo(
                    "prj-1", "origin-1", "order-service", "Order Service", "default",
                    "ws-1", CREATED_AT, CREATED_AT, Map.of(), null);

            RepositoryStatistics stats = RepositoryStatistics.fromCategoryMap(
                    2, RecordingStatus.ACTIVE, CREATED_AT.toEpochMilli(), 1000L, 12, 600L,
                    Map.of(
                            RepositoryStatistics.StatsCategory.JFR, new FileTypeStats(6, 700L),
                            RepositoryStatistics.StatsCategory.HEAP_DUMP, new FileTypeStats(1, 200L),
                            RepositoryStatistics.StatsCategory.LOG, new FileTypeStats(2, 40L),
                            RepositoryStatistics.StatsCategory.APP_LOG, new FileTypeStats(1, 30L),
                            RepositoryStatistics.StatsCategory.ERROR_LOG, new FileTypeStats(1, 20L),
                            RepositoryStatistics.StatsCategory.OTHER, new FileTypeStats(1, 10L)));

            doReturn(List.of(workspaceManager)).when(workspacesManager).findAll();
            when(workspaceManager.localInfo()).thenReturn(workspaceInfo);
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);
            doReturn(List.of(projectManager)).when(projectsManager).findAll();
            when(projectManager.info()).thenReturn(projectInfo);
            when(projectManager.repositoryManager()).thenReturn(repositoryManager);
            when(repositoryManager.calculateRepositoryStatistics()).thenReturn(stats);

            StorageOverview overview = storageManager.overview();

            assertThat(overview.projects()).hasSize(1);
            ProjectStorage project = overview.projects().getFirst();
            assertThat(project.workspaceId()).isEqualTo("ws-1");
            assertThat(project.workspaceName()).isEqualTo("production");
            assertThat(project.projectId()).isEqualTo("prj-1");
            assertThat(project.projectName()).isEqualTo("order-service");
            assertThat(project.projectLabel()).isEqualTo("Order Service");
            assertThat(project.totalSizeBytes()).isEqualTo(1000L);
            assertThat(project.totalFiles()).isEqualTo(12);
            assertThat(project.jfrSizeBytes()).isEqualTo(700L);
            assertThat(project.heapDumpSizeBytes()).isEqualTo(200L);
            assertThat(project.logSizeBytes()).isEqualTo(90L);
            assertThat(project.otherSizeBytes()).isEqualTo(10L);
        }
    }
}
