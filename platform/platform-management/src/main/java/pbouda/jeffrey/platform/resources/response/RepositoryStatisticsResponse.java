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

package pbouda.jeffrey.platform.resources.response;

import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.profile.manager.model.RepositoryStatistics;

public record RepositoryStatisticsResponse(
        int totalSessions,
        RecordingStatus sessionStatus,
        long lastActivityTime,
        long totalSize,
        int totalFiles,
        long biggestSessionSize,
        int jfrFiles,
        long jfrSize,
        int heapDumpFiles,
        long heapDumpSize,
        int logFiles,
        long logSize,
        int appLogFiles,
        long appLogSize,
        int errorLogFiles,
        long errorLogSize,
        int otherFiles,
        long otherSize) {

    public static RepositoryStatisticsResponse from(RepositoryStatistics stats) {
        return new RepositoryStatisticsResponse(
                stats.totalSessions(),
                stats.latestSessionStatus(),
                stats.lastActivityTimeMillis(),
                stats.totalSizeBytes(),
                stats.totalFiles(),
                stats.biggestSessionSizeBytes(),
                stats.jfrFiles(),
                stats.jfrSize(),
                stats.heapDumpFiles(),
                stats.heapDumpSize(),
                stats.logFiles(),
                stats.logSize(),
                stats.appLogFiles(),
                stats.appLogSize(),
                stats.errorLogFiles(),
                stats.errorLogSize(),
                stats.otherFiles(),
                stats.otherSize()
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
                response.jfrSize(),
                response.heapDumpFiles(),
                response.heapDumpSize(),
                response.logFiles(),
                response.logSize(),
                response.appLogFiles(),
                response.appLogSize(),
                response.errorLogFiles(),
                response.errorLogSize(),
                response.otherFiles(),
                response.otherSize()
        );
    }
}
