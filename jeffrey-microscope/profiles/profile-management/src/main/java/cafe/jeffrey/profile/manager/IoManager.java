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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.io.FileForceStats;
import cafe.jeffrey.profile.manager.model.io.IoEndpoint;
import cafe.jeffrey.profile.manager.model.io.IoKind;
import cafe.jeffrey.profile.manager.model.io.IoOperation;
import cafe.jeffrey.profile.manager.model.io.IoOverview;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

/**
 * Socket and file I/O insight for a single profile, from {@code jdk.SocketRead},
 * {@code jdk.SocketWrite}, {@code jdk.FileRead} and {@code jdk.FileWrite}. Methods are scoped to an
 * {@link IoKind} so the socket and file pages each see only their own blocking I/O — throughput over
 * time, slowest operations, and the busiest peers/files.
 */
public interface IoManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, IoManager> {
    }

    /**
     * Headline metrics for the kind: bytes read/written, op count, slowest operation, presence flag.
     */
    IoOverview overview(IoKind kind);

    /**
     * Bytes-read-per-second and bytes-written-per-second across the recording, for the kind.
     */
    TimeseriesData throughputTimeline(IoKind kind);

    /**
     * Slowest individual operations of the kind, ordered by descending duration.
     */
    List<IoOperation> slowestOperations(IoKind kind);

    /**
     * Endpoints ranked by bytes — socket peers ({@code host:port}) for {@code SOCKET}, files (by path)
     * for {@code FILE}. Empty when no events of the kind are present.
     */
    List<IoEndpoint> endpoints(IoKind kind);

    /**
     * File I/O aggregated by parent directory, ranked by bytes; empty when no file events are present.
     */
    List<IoEndpoint> directories();

    /**
     * Fsync (file-force) latency summary from {@code jdk.FileForce} — count, latency stats, and slowest
     * forces. Force events carry no bytes, so they are reported separately from read/write throughput.
     */
    FileForceStats fileForce();
}
