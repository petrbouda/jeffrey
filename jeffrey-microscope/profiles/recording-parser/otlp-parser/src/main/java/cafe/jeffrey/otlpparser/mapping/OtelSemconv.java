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
