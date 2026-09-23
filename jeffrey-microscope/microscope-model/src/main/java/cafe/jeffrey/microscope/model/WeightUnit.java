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

package cafe.jeffrey.microscope.model;

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
