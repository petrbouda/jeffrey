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
 * Request for a flamegraph scoped to a single async-profiler span. Unlike
 * {@link GenerateSpanFlamegraphRequest} (which scopes to <em>all</em> spans of a tag), this carries the
 * span's own interval — its thread and time window — so the flamegraph contains only the samples that one
 * span covers. The backend turns ({@code threadHash}, {@code fromMillis}, {@code toMillis}) into a single
 * {@code SpanInterval}; no separate time range or thread filter is needed.
 */
public record GenerateSingleSpanFlamegraphRequest(
        long threadHash,
        long fromMillis,
        long toMillis,
        Type eventType,
        boolean useThreadMode,
        Boolean useWeight,
        boolean excludeNonJavaSamples,
        boolean excludeIdleSamples,
        boolean onlyUnsafeAllocationSamples,
        GraphComponents components) implements SpanFlamegraphOptions {
}
