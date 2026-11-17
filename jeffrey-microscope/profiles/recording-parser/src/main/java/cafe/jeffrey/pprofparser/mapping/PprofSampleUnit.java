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

package cafe.jeffrey.pprofparser.mapping;

import java.util.Locale;
import java.util.Map;

/**
 * Interpretation of a pprof {@code sample_type} unit, deciding how a sample value maps to Jeffrey's
 * {@code samples} / {@code weight} columns:
 * <ul>
 *   <li>{@link CountUnit} — a number of sampled occurrences ({@code samples} column)</li>
 *   <li>{@link DurationUnit} — a time span, normalized to nanoseconds ({@code weight} column)</li>
 *   <li>{@link BytesUnit} — a byte size ({@code weight} column)</li>
 * </ul>
 * Unknown units are conservatively treated as {@link CountUnit}.
 */
public sealed interface PprofSampleUnit {

    record CountUnit() implements PprofSampleUnit {
    }

    record DurationUnit(long nanosPerUnit) implements PprofSampleUnit {
        public long toNanos(long value) {
            return value * nanosPerUnit;
        }
    }

    record BytesUnit() implements PprofSampleUnit {
    }

    CountUnit COUNT = new CountUnit();
    BytesUnit BYTES = new BytesUnit();
    DurationUnit NANOSECONDS = new DurationUnit(1);
    DurationUnit MICROSECONDS = new DurationUnit(1_000);
    DurationUnit MILLISECONDS = new DurationUnit(1_000_000);
    DurationUnit SECONDS = new DurationUnit(1_000_000_000);

    Map<String, PprofSampleUnit> UNITS_BY_NAME = Map.ofEntries(
            Map.entry("count", COUNT),
            Map.entry("bytes", BYTES),
            Map.entry("nanoseconds", NANOSECONDS),
            Map.entry("microseconds", MICROSECONDS),
            Map.entry("milliseconds", MILLISECONDS),
            Map.entry("seconds", SECONDS));

    static PprofSampleUnit fromUnitString(String unit) {
        if (unit == null || unit.isBlank()) {
            return COUNT;
        }
        PprofSampleUnit resolved = UNITS_BY_NAME.get(unit.toLowerCase(Locale.ROOT));
        return resolved != null ? resolved : COUNT;
    }
}
