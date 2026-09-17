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


import java.util.List;

/**
 * Snapshot of the hub's on-disk storage usage. All byte figures are computed from
 * the filesystem at the time of the call — the hub database stores no size columns.
 */
public record StorageOverview(
        DiskSpace disk,
        InfrastructureUsage infrastructure,
        List<ProjectStorage> projects) {

    /**
     * Capacity of the volume holding the hub's home directory.
     */
    public record DiskSpace(long totalBytes, long usableBytes) {

        public static final DiskSpace UNKNOWN = new DiskSpace(0L, 0L);
    }

    /**
     * Storage used by the hub itself, outside of project repositories.
     */
    public record InfrastructureUsage(long databaseBytes, long tempBytes) {
    }

    /**
     * Storage used by a single project's recording repository, broken down by the kinds the hub
     * tells apart: plain recordings, compressed ones, and everything else. Kinds with no stored
     * files are omitted from {@code fileTypes}.
     */
    public record ProjectStorage(
            String workspaceId,
            String workspaceName,
            String projectId,
            String projectName,
            String projectLabel,
            long totalSizeBytes,
            int totalFiles,
            long lastActivityTimeMillis,
            List<FileTypeUsage> fileTypes,
            List<StoredFile> largestFiles) {
    }

    /**
     * Aggregated usage of one kind of file within one project repository: a
     * {@code HubManagedFile} name for a recording, {@link #OTHER_FILES} for anything the hub does
     * not classify.
     */
    public record FileTypeUsage(String type, long sizeBytes, int fileCount) {

        public static final String OTHER_FILES = "OTHER";
    }

    /**
     * A single stored file, used for the per-project largest-files listing.
     */
    public record StoredFile(String fileName, long sizeBytes) {
    }
}
