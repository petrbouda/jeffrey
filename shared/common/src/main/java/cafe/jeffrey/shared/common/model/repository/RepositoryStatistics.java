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

package cafe.jeffrey.shared.common.model.repository;

/**
 * Aggregate figures about a project's recording repository.
 *
 * <p>Totals only, on purpose. This used to carry a count and a size for each of six buckets — JFR,
 * heap dump, log, app log, error log, other — filled by a switch over {@link ManagedFile}. That
 * switch was the last place anything on the hub side decided what a file type <em>means</em>, and
 * it decided wrong: pprof and OTLP recordings landed in "other", beside the files nothing could
 * classify at all.
 *
 * <p>Nothing here names a kind of file, so adding a {@code ManagedFile} constant changes neither
 * this record nor the RPC that carries it. A caller that wants to know what a particular file is
 * reads {@code fileType} off the session listing, where the hub reports it without acting on it.
 */
public record RepositoryStatistics(
        int totalSessions,
        RecordingStatus latestSessionStatus,
        long lastActivityTimeMillis,
        long totalSizeBytes,
        int totalFiles,
        long biggestSessionSizeBytes) {

    public static final RepositoryStatistics EMPTY =
            new RepositoryStatistics(0, RecordingStatus.UNKNOWN, 0L, 0L, 0, 0L);
}
