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

package cafe.jeffrey.profile.resources.request;

import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.microscope.model.Type;
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
