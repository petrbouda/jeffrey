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
 * The rendering options common to every span-scoped flamegraph request, independent of how the spans are
 * selected. {@link GenerateSpanFlamegraphRequest} picks them by tag, {@link GenerateSingleSpanFlamegraphRequest}
 * by a single interval, {@link GenerateTraceSpanFlamegraphRequest} by a span of a trace, and
 * {@link GenerateTraceOperationFlamegraphRequest} by every trace of one type, but all four
 * produce the same {@code GraphParameters} from these fields — so they share one mapper instead of
 * duplicating it. Records satisfy these accessors for free.
 */
public sealed interface SpanFlamegraphOptions
        permits GenerateSpanFlamegraphRequest, GenerateSingleSpanFlamegraphRequest,
                GenerateTraceSpanFlamegraphRequest, GenerateTraceOperationFlamegraphRequest {

    Type eventType();

    boolean useThreadMode();

    Boolean useWeight();

    boolean excludeNonJavaSamples();

    boolean excludeIdleSamples();

    boolean onlyUnsafeAllocationSamples();

    GraphComponents components();
}
