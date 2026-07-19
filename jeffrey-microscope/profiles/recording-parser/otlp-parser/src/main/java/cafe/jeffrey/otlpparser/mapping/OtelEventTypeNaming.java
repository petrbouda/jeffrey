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

import cafe.jeffrey.shared.common.model.EventTypeName;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps the free-form pprof-style sample type of an OTLP {@code Profile.sample_type} onto Jeffrey's
 * {@code otel.*} event type namespace. Well-known sample types (as emitted by async-profiler's OTLP
 * export, the OTel eBPF profiler and pprof conversions) are folded onto five canonical event types
 * so the frontend can light up the matching flamegraph/timeseries cards; everything else becomes a
 * sanitized {@code otel.<type>} event type that is still browsable in the Event Viewer.
 */
public final class OtelEventTypeNaming {

    public record OtelEventType(String name, String label, List<String> categories) {
    }

    private static final String CATEGORY_OPEN_TELEMETRY = "OpenTelemetry";

    private static final OtelEventType CPU = new OtelEventType(
            EventTypeName.OTEL_CPU, "CPU (OTel)", List.of(CATEGORY_OPEN_TELEMETRY, "CPU"));
    private static final OtelEventType SAMPLES = new OtelEventType(
            EventTypeName.OTEL_SAMPLES, "Execution Samples (OTel)", List.of(CATEGORY_OPEN_TELEMETRY, "CPU"));
    private static final OtelEventType WALL = new OtelEventType(
            EventTypeName.OTEL_WALL, "Wall Clock (OTel)", List.of(CATEGORY_OPEN_TELEMETRY, "Wall-Clock"));
    private static final OtelEventType ALLOC = new OtelEventType(
            EventTypeName.OTEL_ALLOC, "Allocations (OTel)", List.of(CATEGORY_OPEN_TELEMETRY, "Allocation"));
    private static final OtelEventType LOCK = new OtelEventType(
            EventTypeName.OTEL_LOCK, "Lock Contention (OTel)", List.of(CATEGORY_OPEN_TELEMETRY, "Blocking"));

    /**
     * Sample-type aliases observed across producers: async-profiler emits {@code cpu}/{@code wall}/
     * {@code alloc}/{@code lock}, the eBPF profiler emits {@code samples}/{@code count}, pprof heap
     * profiles use {@code alloc_space}/{@code alloc_objects}/{@code allocated_*}, pprof mutex/block
     * profiles use {@code contentions}/{@code delay}.
     */
    private static final Map<String, OtelEventType> WELL_KNOWN_TYPES = Map.ofEntries(
            Map.entry("cpu", CPU),
            Map.entry("cpu_time", CPU),
            Map.entry("samples", SAMPLES),
            Map.entry("events", SAMPLES),
            Map.entry("wall", WALL),
            Map.entry("wallclock", WALL),
            Map.entry("wall_time", WALL),
            Map.entry("alloc", ALLOC),
            Map.entry("allocations", ALLOC),
            Map.entry("alloc_space", ALLOC),
            Map.entry("alloc_objects", ALLOC),
            Map.entry("allocated_space", ALLOC),
            Map.entry("allocated_objects", ALLOC),
            Map.entry("alloc_samples", ALLOC),
            Map.entry("lock", LOCK),
            Map.entry("locks", LOCK),
            Map.entry("block", LOCK),
            Map.entry("mutex", LOCK),
            Map.entry("contentions", LOCK),
            Map.entry("delay", LOCK));

    private static final Set<Character> ALLOWED_NAME_CHARACTERS = Set.of('_', '-');

    private OtelEventTypeNaming() {
    }

    /**
     * @param sampleType the resolved {@code sample_type.type} string of the profile (may be blank)
     * @param sampleUnit the resolved {@code sample_type.unit} string of the profile (used only for
     *                   labeling custom types)
     */
    public static OtelEventType resolve(String sampleType, String sampleUnit) {
        if (sampleType == null || sampleType.isBlank()) {
            return SAMPLES;
        }
        OtelEventType wellKnown = WELL_KNOWN_TYPES.get(sampleType.toLowerCase(Locale.ROOT));
        if (wellKnown != null) {
            return wellKnown;
        }
        return customType(sampleType, sampleUnit);
    }

    private static OtelEventType customType(String sampleType, String sampleUnit) {
        String name = EventTypeName.OTEL_NAMESPACE + sanitize(sampleType);
        String label = (sampleUnit == null || sampleUnit.isBlank())
                ? sampleType + " (OTel)"
                : sampleType + " [" + sampleUnit + "] (OTel)";
        return new OtelEventType(name, label, List.of(CATEGORY_OPEN_TELEMETRY));
    }

    private static String sanitize(String sampleType) {
        StringBuilder sanitized = new StringBuilder(sampleType.length());
        for (char c : sampleType.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c) || ALLOWED_NAME_CHARACTERS.contains(c)) {
                sanitized.append(c);
            } else {
                sanitized.append('_');
            }
        }
        return sanitized.toString();
    }
}
