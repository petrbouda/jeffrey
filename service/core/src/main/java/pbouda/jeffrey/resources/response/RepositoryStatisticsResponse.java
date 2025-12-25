/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.resources.response;

import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.profile.manager.model.RepositoryStatistics;

public record RepositoryStatisticsResponse(
        int totalSessions,
        RecordingStatus sessionStatus,
        long lastActivityTime,
        long totalSize,
        int totalFiles,
        long biggestSessionSize,
        int jfrFiles,
        int heapDumpFiles,
        int otherFiles) {

    public static RepositoryStatisticsResponse from(RepositoryStatistics stats) {
        return new RepositoryStatisticsResponse(
                stats.totalSessions(),
                stats.latestSessionStatus(),
                stats.lastActivityTimeMillis(),
                stats.totalSizeBytes(),
                stats.totalFiles(),
                stats.biggestSessionSizeBytes(),
                stats.jfrFiles(),
                stats.heapDumpFiles(),
                stats.otherFiles()
        );
    }

    public static RepositoryStatistics from(RepositoryStatisticsResponse response) {
        return new RepositoryStatistics(
                response.totalSessions(),
                response.sessionStatus(),
                response.lastActivityTime(),
                response.totalSize(),
                response.totalFiles(),
                response.biggestSessionSize(),
                response.jfrFiles(),
                response.heapDumpFiles(),
                response.otherFiles()
        );
    }
}
