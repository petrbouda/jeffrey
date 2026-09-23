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
