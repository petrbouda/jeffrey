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

package cafe.jeffrey.hub.model.repository;

import java.util.List;

/**
 * Aggregated storage statistics for a single project instance.
 * Computed by walking the instance's session directories on disk.
 */
public record InstanceStats(int fileCount, long totalSizeBytes) {

    /** Over sessions loaded with their files; headers alone would count every session as empty. */
    public static InstanceStats of(List<RecordingSession> sessions) {
        int fileCount = sessions.stream().mapToInt(session -> session.files().size()).sum();
        long totalSize = sessions.stream().mapToLong(RecordingSession::totalSizeBytes).sum();
        return new InstanceStats(fileCount, totalSize);
    }
}
