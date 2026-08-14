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

package cafe.jeffrey.profile.resources.request;

import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.profile.common.config.GraphComponents;

/**
 * Request for a flamegraph scoped to every trace of one type. Carries no time range or thread — the
 * backend derives both from the traces of that type, so the graph contains only the samples those
 * traces cover.
 * <p>
 * The trace type takes three fields, not just the name: an inbound {@code GET /orders} and an
 * outbound call to the same path share a name, and a graph scoped to only the name would mix them.
 *
 * @param name                the root span's operation name
 * @param kind                the role the root played, a {@code SpanKind} name such as {@code SERVER}
 * @param rootEventType       the event type that opened the trace, e.g. {@code jeffrey.HttpServerExchange};
 *                            named apart from {@link #eventType()}, which selects the samples to draw
 * @param eventType           which recorded event the flamegraph is built from
 * @param useThreadMode       split the graph by thread
 * @param useWeight           weight frames by the event's value rather than by sample count
 * @param excludeNonJavaSamples   drop samples with no Java frames
 * @param excludeIdleSamples      drop samples taken while the thread was parked
 * @param onlyUnsafeAllocationSamples  keep only allocations made through {@code Unsafe}
 * @param components          which parts of the graph to build
 */
public record GenerateTraceOperationFlamegraphRequest(
        String name,
        String kind,
        String rootEventType,
        Type eventType,
        boolean useThreadMode,
        Boolean useWeight,
        boolean excludeNonJavaSamples,
        boolean excludeIdleSamples,
        boolean onlyUnsafeAllocationSamples,
        GraphComponents components) implements SpanFlamegraphOptions {

    /** The trace type this graph is scoped to. */
    public TraceOperationId operationId() {
        return new TraceOperationId(name, kind, rootEventType);
    }
}
