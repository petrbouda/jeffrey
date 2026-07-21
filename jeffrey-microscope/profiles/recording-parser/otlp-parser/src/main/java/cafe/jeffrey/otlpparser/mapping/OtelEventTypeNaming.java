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
