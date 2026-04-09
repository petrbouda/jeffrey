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

package pbouda.jeffrey.shared.common.model.repository;

import java.util.Map;

/**
 * Repository statistics containing aggregated information about sessions, files, and storage.
 */
public record RepositoryStatistics(
        int totalSessions,
        RecordingStatus latestSessionStatus,
        long lastActivityTimeMillis,
        long totalSizeBytes,
        int totalFiles,
        long biggestSessionSizeBytes,
        FileTypeStats jfr,
        FileTypeStats heapDump,
        FileTypeStats log,
        FileTypeStats appLog,
        FileTypeStats errorLog,
        FileTypeStats other) {

    public static final RepositoryStatistics EMPTY = new RepositoryStatistics(
            0, RecordingStatus.UNKNOWN, 0L, 0L, 0, 0L,
            FileTypeStats.EMPTY, FileTypeStats.EMPTY, FileTypeStats.EMPTY,
            FileTypeStats.EMPTY, FileTypeStats.EMPTY, FileTypeStats.EMPTY);

    public static RepositoryStatistics fromCategoryMap(
            int totalSessions,
            RecordingStatus latestSessionStatus,
            long lastActivityTimeMillis,
            long totalSizeBytes,
            int totalFiles,
            long biggestSessionSizeBytes,
            Map<StatsCategory, FileTypeStats> byCategory) {

        return new RepositoryStatistics(
                totalSessions,
                latestSessionStatus,
                lastActivityTimeMillis,
                totalSizeBytes,
                totalFiles,
                biggestSessionSizeBytes,
                byCategory.getOrDefault(StatsCategory.JFR, FileTypeStats.EMPTY),
                byCategory.getOrDefault(StatsCategory.HEAP_DUMP, FileTypeStats.EMPTY),
                byCategory.getOrDefault(StatsCategory.LOG, FileTypeStats.EMPTY),
                byCategory.getOrDefault(StatsCategory.APP_LOG, FileTypeStats.EMPTY),
                byCategory.getOrDefault(StatsCategory.ERROR_LOG, FileTypeStats.EMPTY),
                byCategory.getOrDefault(StatsCategory.OTHER, FileTypeStats.EMPTY));
    }

    public record FileTypeStats(int count, long size) {
        public static final FileTypeStats EMPTY = new FileTypeStats(0, 0L);
    }

    public enum StatsCategory {
        JFR, HEAP_DUMP, LOG, APP_LOG, ERROR_LOG, OTHER;

        public static StatsCategory of(SupportedRecordingFile fileType) {
            return switch (fileType) {
                case JFR, JFR_LZ4 -> JFR;
                case HEAP_DUMP, HEAP_DUMP_GZ -> HEAP_DUMP;
                case JVM_LOG -> LOG;
                case APP_LOG -> APP_LOG;
                case HS_JVM_ERROR_LOG -> ERROR_LOG;
                default -> OTHER;
            };
        }
    }
}
