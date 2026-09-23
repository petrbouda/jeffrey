/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.io.FileForceStats;
import cafe.jeffrey.profile.manager.model.io.IoEndpoint;
import cafe.jeffrey.profile.manager.model.io.IoEndpointTimeline;
import cafe.jeffrey.profile.manager.model.io.IoKind;
import cafe.jeffrey.profile.manager.model.io.IoMetric;
import cafe.jeffrey.profile.manager.model.io.IoOperation;
import cafe.jeffrey.profile.manager.model.io.IoOverview;
import cafe.jeffrey.profile.manager.model.io.IoTargetFilter;
import cafe.jeffrey.microscope.model.ProfileInfo;
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
     * The per-second I/O timeline for the kind — either across every endpoint
     * ({@link IoTargetFilter#all()}) or scoped to a single peer/file, so one endpoint can be read
     * against the aggregate.
     * <p>
     * Four series, in order: bytes read, bytes written, read operations, write operations. Callers
     * pick the pair that matches the metric they are showing.
     */
    TimeseriesData timeline(IoKind kind, IoTargetFilter targetFilter);

    /**
     * The top endpoints of the kind, each paired with its per-second shape, capped so a recording
     * with thousands of peers stays cheap to render. Empty when no events of the kind are present.
     * <p>
     * The metric selects both the series and the ranking, and ranking precedes the cap — so
     * {@link IoMetric#BYTES} and {@link IoMetric#COUNT} return genuinely different sets of
     * endpoints, not the same set in a different order.
     */
    List<IoEndpointTimeline> endpointTimelines(IoKind kind, IoMetric metric);

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
