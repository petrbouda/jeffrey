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

/**
 * Maps a pprof {@code sample_type} (a {@code type}/{@code unit} pair, e.g. {@code cpu}/{@code
 * nanoseconds} or {@code alloc_space}/{@code bytes}) onto a Jeffrey event type. A single pprof profile
 * carries several value dimensions (a Go heap profile has four: alloc/inuse × objects/space); each
 * becomes its own event type so they stay independently browsable, matching how {@code go tool pprof}
 * lets you switch the sample index.
 *
 * <p>Nothing is transformed: the event code and label are the raw sample-type name verbatim. The
 * profile's format (pprof) is set explicitly at import, not inferred from the code.
 */
public final class PprofEventTypeNaming {

    public record PprofEventType(String name, String label, String sampleType) {
    }

    private static final String DEFAULT_TYPE = "samples";
    private static final String SAMPLE_TYPE_SEPARATOR = "/";

    private PprofEventTypeNaming() {
    }

    public static PprofEventType resolve(String sampleType, String sampleUnit) {
        String type = sampleType == null || sampleType.isBlank() ? DEFAULT_TYPE : sampleType;
        // The original pprof sample_type as `type/unit` (e.g. `samples/count`, `cpu/nanoseconds`),
        // preserved so the UI and the flamegraph weight formatting can read the unit.
        String sampleTypeWithUnit = sampleUnit == null || sampleUnit.isBlank()
                ? type
                : type + SAMPLE_TYPE_SEPARATOR + sampleUnit;
        return new PprofEventType(type, type, sampleTypeWithUnit);
    }
}
