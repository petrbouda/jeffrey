/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.manager.model;

import pbouda.jeffrey.common.model.repository.RecordingStatus;

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
        int jfrFiles,
        int heapDumpFiles,
        int otherFiles
) {}