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
