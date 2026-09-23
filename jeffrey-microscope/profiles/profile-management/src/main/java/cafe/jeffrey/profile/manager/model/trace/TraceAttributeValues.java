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

package cafe.jeffrey.profile.manager.model.trace;

import java.util.List;

/**
 * One key's values, ranked, with what each cost.
 *
 * @param values           the ranked values
 * @param tracesWithoutKey traces carrying the key on no span at all — stated rather than left out,
 *                         because a key missing from a fifth of the profile is a fact about the
 *                         instrumentation, and hiding it makes every share on the screen a lie
 * @param distinctValues   how many values the key has altogether
 * @param truncated        whether {@link #values()} is only the top of them
 */
public record TraceAttributeValues(
        List<Row> values,
        long tracesWithoutKey,
        long distinctValues,
        boolean truncated) {

    /**
     * One value of the key.
     * <p>
     * A trace counts towards every value it carried — a trace whose spans recorded two tenants
     * appears under both — so these counts do not sum to the profile's trace count. That is what
     * per-span attributes mean, not a defect in the arithmetic.
     */
    public record Row(
            String value,
            long traceCount,
            long totalNanos,
            long p50Nanos,
            long p95Nanos,
            long maxNanos,
            long errorTraces) {
    }
}
