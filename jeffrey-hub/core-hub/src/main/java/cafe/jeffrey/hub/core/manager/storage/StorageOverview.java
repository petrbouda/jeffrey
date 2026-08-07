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
    public record InfrastructureUsage(long databaseBytes, long queueBytes, long tempBytes) {
    }

    /**
     * Storage used by a single project's recording repository, broken down by file category.
     * Log bytes aggregate JVM, application, and error logs.
     */
    public record ProjectStorage(
            String workspaceId,
            String workspaceName,
            String projectId,
            String projectName,
            String projectLabel,
            long totalSizeBytes,
            int totalFiles,
            long jfrSizeBytes,
            long heapDumpSizeBytes,
            long logSizeBytes,
            long otherSizeBytes) {
    }
}
