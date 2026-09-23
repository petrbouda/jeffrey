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
 * Request for a flamegraph scoped to a single async-profiler span {@code tag}. Unlike
 * {@link GenerateFlamegraphRequest}, it carries no time range or thread — the backend derives both
 * from the spans of {@code tag} (their thread + window), so the flamegraph contains only the samples
 * those spans actually cover.
 */
public record GenerateSpanFlamegraphRequest(
        String tag,
        Type eventType,
        boolean useThreadMode,
        Boolean useWeight,
        boolean excludeNonJavaSamples,
        boolean excludeIdleSamples,
        boolean onlyUnsafeAllocationSamples,
        GraphComponents components) implements SpanFlamegraphOptions {
}
