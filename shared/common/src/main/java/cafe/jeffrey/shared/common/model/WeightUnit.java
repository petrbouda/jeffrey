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

package cafe.jeffrey.shared.common.model;

import java.util.Locale;

/**
 * How a sample's weight is measured, derived from the recording's sample unit. Aggregated stack-sample
 * formats (pprof / OTLP) carry the unit as {@code type/unit} in the event type's extras; it drives both
 * the flamegraph card's weight formatting and the generator's builder/frame-processor selection, so those
 * agree without matching on the event code. {@link #NONE} covers count units and JFR (no stored unit).
 */
public enum WeightUnit {
    BYTES,
    DURATION,
    NONE;

    private static final String BYTES_TOKEN = "byte";
    private static final String NANOS_TOKEN = "nano";
    private static final String SECONDS_TOKEN = "second";
    private static final String SAMPLE_TYPE_SEPARATOR = "/";

    public static WeightUnit fromUnit(String unit) {
        if (unit == null) {
            return NONE;
        }
        String normalized = unit.toLowerCase(Locale.ROOT);
        if (normalized.contains(BYTES_TOKEN)) {
            return BYTES;
        }
        if (normalized.contains(NANOS_TOKEN) || normalized.contains(SECONDS_TOKEN)) {
            return DURATION;
        }
        return NONE;
    }

    /**
     * @param sampleType the stored {@code type/unit} string (e.g. {@code alloc_space/bytes}), or null
     * @return the weight unit parsed from the unit half, or {@link #NONE} if absent
     */
    public static WeightUnit fromSampleType(String sampleType) {
        if (sampleType == null) {
            return NONE;
        }
        int separator = sampleType.indexOf(SAMPLE_TYPE_SEPARATOR);
        return separator < 0 ? NONE : fromUnit(sampleType.substring(separator + 1));
    }
}
