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

import java.util.Set;

/**
 * OpenTelemetry semantic-convention attribute keys consumed structurally by the OTLP parser.
 */
public final class OtelSemconv {

    public static final String THREAD_NAME = "thread.name";
    public static final String THREAD_ID = "thread.id";
    public static final String PROFILE_FRAME_TYPE = "profile.frame.type";
    public static final String SERVICE_NAME = "service.name";
    public static final String SERVICE_INSTANCE_ID = "service.instance.id";
    public static final String PROCESS_EXECUTABLE_NAME = "process.executable.name";
    public static final String PROCESS_PID = "process.pid";

    /**
     * Scope provenance keys synthesized by the parser (not part of semconv) for the Event Types view.
     */
    public static final String SCOPE_NAME_SETTING = "otel.scope.name";
    public static final String SCOPE_VERSION_SETTING = "otel.scope.version";

    /**
     * Attribute keys carrying the entity (class) associated with an allocation sample's weight.
     */
    public static final Set<String> WEIGHT_ENTITY_KEYS = Set.of("class", "allocation.class", "jvm.class.name");

    /**
     * Sample attribute keys that are consumed structurally (mapped to dedicated columns) and are
     * therefore excluded from the generic per-event JSON fields.
     */
    public static final Set<String> STRUCTURAL_SAMPLE_KEYS = Set.of(THREAD_NAME, THREAD_ID, PROFILE_FRAME_TYPE);

    private OtelSemconv() {
    }
}
