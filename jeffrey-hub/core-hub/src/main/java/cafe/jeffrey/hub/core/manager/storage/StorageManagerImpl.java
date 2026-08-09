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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.DiskSpace;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.FileTypeUsage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.InfrastructureUsage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.ProjectStorage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.StoredFile;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class StorageManagerImpl implements StorageManager {

    private static final Logger LOG = LoggerFactory.getLogger(StorageManagerImpl.class);

    /**
     * Default database files inside the hub's home directory. A custom
     * {@code jeffrey.hub.persistence.database.url} pointing elsewhere is not measured.
     */
    private static final String DATABASE_FILE_NAME = "jeffrey-data.db";
    private static final String DATABASE_WAL_FILE_NAME = "jeffrey-data.db.wal";

    /**
     * How many of the project's biggest files are listed in the per-project breakdown.
     */
    private static final int LARGEST_FILES_LIMIT = 3;

    private final WorkspacesManager workspacesManager;
    private final HubJeffreyDirs jeffreyDirs;

    public StorageManagerImpl(WorkspacesManager workspacesManager, HubJeffreyDirs jeffreyDirs) {
        this.workspacesManager = workspacesManager;
        this.jeffreyDirs = jeffreyDirs;
    }

    @Override
    public StorageOverview overview() {
        return new StorageOverview(diskSpace(), infrastructureUsage(), collectProjects());
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
        List<RepositoryFile> files = projectManager.repositoryManager().listRecordingSessions(true).stream()
                .flatMap(session -> session.files().stream())
                .toList();

        long totalSize = files.stream()
                .mapToLong(StorageManagerImpl::fileSize)
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
                lastActivity,
                fileTypeUsages(files),
                largestFiles(files));
    }

    private static List<FileTypeUsage> fileTypeUsages(List<RepositoryFile> files) {
        Map<SupportedRecordingFile, List<RepositoryFile>> byType = files.stream()
                .collect(Collectors.groupingBy(RepositoryFile::fileType));

        return byType.entrySet().stream()
                .map(entry -> new FileTypeUsage(
                        entry.getKey(),
                        entry.getValue().stream().mapToLong(StorageManagerImpl::fileSize).sum(),
                        entry.getValue().size()))
                .sorted(Comparator.comparingLong(FileTypeUsage::sizeBytes).reversed())
                .toList();
    }

    private static List<StoredFile> largestFiles(List<RepositoryFile> files) {
        return files.stream()
                .sorted(Comparator.comparingLong(StorageManagerImpl::fileSize).reversed())
                .limit(LARGEST_FILES_LIMIT)
                .map(file -> new StoredFile(file.name(), fileSize(file)))
                .toList();
    }

    private static long fileSize(RepositoryFile file) {
        return file.size() != null ? file.size() : 0L;
    }

    private InfrastructureUsage infrastructureUsage() {
        long databaseBytes = fileSizeIfExists(jeffreyDirs.homeDir().resolve(DATABASE_FILE_NAME))
                + fileSizeIfExists(jeffreyDirs.homeDir().resolve(DATABASE_WAL_FILE_NAME));
        long queueBytes = FileSystemUtils.directorySize(jeffreyDirs.workspaceEvents());
        long tempBytes = FileSystemUtils.directorySize(jeffreyDirs.temp());
        return new InfrastructureUsage(databaseBytes, queueBytes, tempBytes);
    }

    private DiskSpace diskSpace() {
        try {
            FileStore fileStore = Files.getFileStore(jeffreyDirs.homeDir());
            return new DiskSpace(fileStore.getTotalSpace(), fileStore.getUsableSpace());
        } catch (IOException e) {
            LOG.warn("Cannot resolve disk space of the home directory: home_dir={}", jeffreyDirs.homeDir());
            return DiskSpace.UNKNOWN;
        }
    }

    private static long fileSizeIfExists(Path file) {
        if (!Files.isRegularFile(file)) {
            return 0L;
        }
        return FileSystemUtils.size(file);
    }
}
