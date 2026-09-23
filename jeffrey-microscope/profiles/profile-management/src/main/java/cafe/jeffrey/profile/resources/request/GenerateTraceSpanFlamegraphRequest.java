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

import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.common.config.GraphComponents;

/**
 * Request for a flamegraph scoped to one span of a trace. The span is identified by the path, not
 * the body, so this carries only the rendering options plus how much of the span to cover.
 *
 * @param selfOnly when {@code true}, the span's children are cut out of the window so the graph
 *                 shows only the work the span did itself — the difference between "this request
 *                 took 400 ms" and "this request spent 400 ms in code it owns"
 */
public record GenerateTraceSpanFlamegraphRequest(
        boolean selfOnly,
        Type eventType,
        boolean useThreadMode,
        Boolean useWeight,
        boolean excludeNonJavaSamples,
        boolean excludeIdleSamples,
        boolean onlyUnsafeAllocationSamples,
        GraphComponents components) implements SpanFlamegraphOptions {
}
