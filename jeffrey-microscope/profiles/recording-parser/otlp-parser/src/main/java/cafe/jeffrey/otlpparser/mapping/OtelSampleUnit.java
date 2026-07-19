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

package cafe.jeffrey.otlpparser.mapping;

import java.util.Locale;
import java.util.Map;

/**
 * Interpretation of the unit string of an OTLP {@code Profile.sample_type}. The unit decides how a
 * sample value maps to Jeffrey's {@code samples}/{@code weight} event columns:
 * <ul>
 *   <li>{@link CountUnit} — the value is a number of sampled occurrences ({@code samples} column)</li>
 *   <li>{@link DurationUnit} — the value is a time span, normalized to nanoseconds ({@code weight} column)</li>
 *   <li>{@link BytesUnit} — the value is a byte size ({@code weight} column)</li>
 * </ul>
 * Unknown units are conservatively treated as {@link CountUnit}.
 */
public sealed interface OtelSampleUnit {

    record CountUnit() implements OtelSampleUnit {
    }

    record DurationUnit(long nanosPerUnit) implements OtelSampleUnit {
        public long toNanos(long value) {
            return value * nanosPerUnit;
        }
    }

    record BytesUnit() implements OtelSampleUnit {
    }

    CountUnit COUNT = new CountUnit();
    BytesUnit BYTES = new BytesUnit();
    DurationUnit NANOSECONDS = new DurationUnit(1);
    DurationUnit MICROSECONDS = new DurationUnit(1_000);
    DurationUnit MILLISECONDS = new DurationUnit(1_000_000);
    DurationUnit SECONDS = new DurationUnit(1_000_000_000);

    /**
     * Unit aliases as observed in the wild: pprof-style long names, UCUM codes and common
     * abbreviations used by OTLP profile producers.
     */
    Map<String, OtelSampleUnit> UNITS_BY_NAME = Map.ofEntries(
            Map.entry("count", COUNT),
            Map.entry("1", COUNT),
            Map.entry("bytes", BYTES),
            Map.entry("by", BYTES),
            Map.entry("byte", BYTES),
            Map.entry("nanoseconds", NANOSECONDS),
            Map.entry("nanosecond", NANOSECONDS),
            Map.entry("ns", NANOSECONDS),
            Map.entry("microseconds", MICROSECONDS),
            Map.entry("microsecond", MICROSECONDS),
            Map.entry("us", MICROSECONDS),
            Map.entry("milliseconds", MILLISECONDS),
            Map.entry("millisecond", MILLISECONDS),
            Map.entry("ms", MILLISECONDS),
            Map.entry("seconds", SECONDS),
            Map.entry("second", SECONDS),
            Map.entry("s", SECONDS));

    static OtelSampleUnit fromUnitString(String unit) {
        if (unit == null || unit.isBlank()) {
            return COUNT;
        }
        OtelSampleUnit resolved = UNITS_BY_NAME.get(unit.toLowerCase(Locale.ROOT));
        return resolved != null ? resolved : COUNT;
    }
}
