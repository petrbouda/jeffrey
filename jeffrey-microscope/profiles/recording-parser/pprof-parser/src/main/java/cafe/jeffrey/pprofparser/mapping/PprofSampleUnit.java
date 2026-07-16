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
