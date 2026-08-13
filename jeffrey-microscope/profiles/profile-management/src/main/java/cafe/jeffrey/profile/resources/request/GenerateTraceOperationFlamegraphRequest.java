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
 * Request for a flamegraph scoped to every trace of one type. Carries no time range or thread — the
 * backend derives both from the traces of {@code name}, so the graph contains only the samples those
 * traces cover.
 *
 * @param name the trace type, identified by its root span's operation name
 */
public record GenerateTraceOperationFlamegraphRequest(
        String name,
        Type eventType,
        boolean useThreadMode,
        Boolean useWeight,
        boolean excludeNonJavaSamples,
        boolean excludeIdleSamples,
        boolean onlyUnsafeAllocationSamples,
        GraphComponents components) implements SpanFlamegraphOptions {
}
