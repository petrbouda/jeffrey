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

import cafe.jeffrey.shared.common.model.Type;
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
