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

package cafe.jeffrey.shared.common.model.repository;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RecordingSession(
        String id,
        String name,
        String instanceId,
        Instant createdAt,
        Instant finishedAt,
        RecordingStatus status,
        Path absolutePath,
        Path absoluteStreamingPath,
        List<RepositoryFile> files,
        boolean retained) {

    /**
     * Total size in bytes of every file in this session. Zero when the session was
     * loaded without files. Files whose size could not be determined count as zero
     * rather than failing the whole sum.
     */
    public long totalSizeBytes() {
        return files.stream()
                .map(RepositoryFile::size)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }
}

