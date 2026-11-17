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

package cafe.jeffrey.otlpparser.mapping;

/**
 * Maps the sample type of an OTLP {@code Profile.sample_type} onto an event type. Nothing is
 * transformed: the event code and label are the raw sample-type name verbatim. The profile's format
 * (OpenTelemetry) is set explicitly at import, not inferred from the code.
 */
public final class OtelEventTypeNaming {

    public record OtelEventType(String name, String label) {
    }

    private static final String DEFAULT_TYPE = "samples";

    private OtelEventTypeNaming() {
    }

    public static OtelEventType resolve(String sampleType) {
        String type = sampleType == null || sampleType.isBlank() ? DEFAULT_TYPE : sampleType;
        return new OtelEventType(type, type);
    }
}
