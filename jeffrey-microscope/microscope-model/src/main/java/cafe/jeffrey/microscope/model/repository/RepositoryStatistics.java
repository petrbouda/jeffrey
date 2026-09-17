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

package cafe.jeffrey.microscope.model.repository;

/**
 * What a project's repository occupies on the hub.
 *
 * <p>One figure, because one figure is what anything actually read. This carried a status, a
 * session count, a file count, a last-activity timestamp, a biggest-session size and a count and
 * a size for each of six file-type buckets. The buckets were the last place anything on the hub
 * side decided what a file type <em>means</em>, and they decided wrong: pprof and OTLP recordings
 * landed in "other", beside the files nothing could classify at all. Of what remained, the status
 * had no reader anywhere, and the rest sat beside the same numbers the page already had.
 *
 * <p>Nothing here names a kind of file, so adding a kind of file changes neither
 * this record nor the RPC that carries it. A caller that wants to know what a particular file is
 * reads {@code fileType} off the session listing, where the hub reports it without acting on it.
 */
public record RepositoryStatistics(long totalSizeBytes) {
}
