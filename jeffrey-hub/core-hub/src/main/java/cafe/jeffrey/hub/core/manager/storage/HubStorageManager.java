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

import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.InfrastructureUsage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.ProjectStorage;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HubStorageManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubStorageManager.class);

    /**
     * Default database files inside the hub's home directory. A custom
     * {@code jeffrey.hub.persistence.database.url} pointing elsewhere is not measured.
     */
    private static final String DATABASE_FILE_NAME = "jeffrey-data.db";
    private static final String DATABASE_WAL_FILE_NAME = "jeffrey-data.db.wal";

    private final WorkspacesManager workspacesManager;
    private final HubJeffreyDirs jeffreyDirs;

    public HubStorageManager(WorkspacesManager workspacesManager, HubJeffreyDirs jeffreyDirs) {
        this.workspacesManager = workspacesManager;
        this.jeffreyDirs = jeffreyDirs;
    }

    /** A fresh overview of what every project occupies on the volume; callers read the cached one. */
    public StorageOverview overview() {
        return new StorageOverview(infrastructureUsage(), collectProjects());
    }

    private List<ProjectStorage> collectProjects() {
        List<ProjectStorage> result = new ArrayList<>();
        for (WorkspaceManager workspaceManager : workspacesManager.findAll()) {
            WorkspaceInfo workspaceInfo = workspaceManager.localInfo();
            for (ProjectManager projectManager : workspaceManager.projectsManager().findAll()) {
                result.add(toProjectStorage(workspaceInfo, projectManager));
            }
        }
        return result;
    }

    private static ProjectStorage toProjectStorage(WorkspaceInfo workspaceInfo, ProjectManager projectManager) {
        ProjectInfo projectInfo = projectManager.info();
        List<RepositoryFile> files = projectManager.repositoryManager().listRecordingSessions(SessionDetail.WITH_FILES).stream()
                .flatMap(session -> session.files().stream())
                .toList();

        long totalSize = files.stream()
                .mapToLong(RepositoryFile::size)
                .sum();

        long lastActivity = files.stream()
                .map(RepositoryFile::createdAt)
                .filter(Objects::nonNull)
                .mapToLong(Instant::toEpochMilli)
                .max()
                .orElse(0L);

        return new ProjectStorage(
                workspaceInfo.id(),
                workspaceInfo.name(),
                projectInfo.id(),
                projectInfo.name(),
                projectInfo.label(),
                totalSize,
                files.size(),
                lastActivity);
    }

    private InfrastructureUsage infrastructureUsage() {
        long databaseBytes = fileSizeIfExists(jeffreyDirs.homeDir().resolve(DATABASE_FILE_NAME))
                + fileSizeIfExists(jeffreyDirs.homeDir().resolve(DATABASE_WAL_FILE_NAME));
        long tempBytes = FileSystemUtils.directorySize(jeffreyDirs.temp());
        return new InfrastructureUsage(databaseBytes, tempBytes);
    }

    private static long fileSizeIfExists(Path file) {
        if (!Files.isRegularFile(file)) {
            return 0L;
        }
        return FileSystemUtils.size(file);
    }
}
