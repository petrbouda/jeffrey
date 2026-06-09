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
 * The rendering options common to every span-scoped flamegraph request, independent of how the spans are
 * selected. {@link GenerateSpanFlamegraphRequest} picks them by tag and {@link GenerateSingleSpanFlamegraphRequest}
 * by a single interval, but both produce the same {@code GraphParameters} from these fields — so they share one
 * mapper instead of duplicating it. Records satisfy these accessors for free.
 */
public sealed interface SpanFlamegraphOptions
        permits GenerateSpanFlamegraphRequest, GenerateSingleSpanFlamegraphRequest {

    Type eventType();

    boolean useThreadMode();

    Boolean useWeight();

    boolean excludeNonJavaSamples();

    boolean excludeIdleSamples();

    boolean onlyUnsafeAllocationSamples();

    GraphComponents components();
}
