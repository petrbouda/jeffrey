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

package cafe.jeffrey.flamegraph.ai;

import cafe.jeffrey.shared.common.BytesUtils;
import cafe.jeffrey.shared.common.DurationUtils;
import cafe.jeffrey.shared.common.model.Type;

import java.util.function.LongFunction;

/**
 * What one event type is measured in, and how to write those measurements down.
 * <p>
 * Shared by every AI-facing export so a single profile and a comparison of two of them agree on what a
 * number means: the tree always counts samples, and a weighted event type carries a second dimension —
 * bytes for allocation, nanoseconds for blocking and method latency — that is the one the question is
 * usually about. An export that resolved this for itself would eventually disagree with its neighbour
 * about whether {@code jdk.ObjectAllocationSample} is weighed in bytes.
 *
 * @param unit           what the tree counts, always samples
 * @param weightUnit     what a sample's weight is in, or {@code null} for an unweighted event such as
 *                       a CPU sample
 * @param totalFormatter a header total, with the noun that says what it is ("... Allocated"), or
 *                       {@code null} when unweighted
 * @param valueFormatter a bare per-frame value in the weight unit, or {@code null} when unweighted
 */
public record WeightContext(
        String unit,
        String weightUnit,
        LongFunction<String> totalFormatter,
        LongFunction<String> valueFormatter) {

    private static final String UNIT_SAMPLES = "samples";
    private static final String UNIT_BYTES = "bytes";
    private static final String UNIT_NANOSECONDS = "nanoseconds";

    private static final LongFunction<String> ALLOCATION_TOTAL_FORMATTER =
            weight -> BytesUtils.format(weight) + " Allocated";
    private static final LongFunction<String> BLOCKING_TOTAL_FORMATTER =
            weight -> DurationUtils.formatNanos2Units(weight) + " Blocked";
    private static final LongFunction<String> LATENCY_TOTAL_FORMATTER =
            weight -> DurationUtils.formatNanos2Units(weight) + " Latency";

    /** Per-frame weights carry the value alone; the header's noun would repeat on every line. */
    private static final LongFunction<String> BYTES_VALUE_FORMATTER = BytesUtils::format;
    private static final LongFunction<String> NANOS_VALUE_FORMATTER = DurationUtils::formatNanos2Units;

    private static final WeightContext UNWEIGHTED =
            new WeightContext(UNIT_SAMPLES, null, null, null);

    public static WeightContext of(Type eventType) {
        if (eventType.isAllocationEvent()) {
            return new WeightContext(
                    UNIT_SAMPLES, UNIT_BYTES, ALLOCATION_TOTAL_FORMATTER, BYTES_VALUE_FORMATTER);
        }
        if (eventType.isBlockingEvent()) {
            return new WeightContext(
                    UNIT_SAMPLES, UNIT_NANOSECONDS, BLOCKING_TOTAL_FORMATTER, NANOS_VALUE_FORMATTER);
        }
        if (eventType.isMethodTraceEvent()) {
            return new WeightContext(
                    UNIT_SAMPLES, UNIT_NANOSECONDS, LATENCY_TOTAL_FORMATTER, NANOS_VALUE_FORMATTER);
        }
        return UNWEIGHTED;
    }

    public boolean weighted() {
        return weightUnit != null;
    }

    /**
     * A measurement written for a reader: the weight in its own unit on a weighted event type, the
     * bare sample count otherwise. Lets a caller render a number without first asking which it holds.
     */
    public String format(long value) {
        return weighted() ? valueFormatter.apply(value) : Long.toString(value);
    }
}
