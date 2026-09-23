/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provider.profile.api;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.microscope.model.ThreadInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.jfrparser.api.type.JfrThread;

import java.time.Duration;
import java.time.Instant;

public record GenericRecord(
        Type type,
        String typeLabel,
        Instant startTimestamp,
        Duration timestampFromStart,
        Duration duration,
        JfrThread thread,
        JfrMethod weightEntity,
        long samples,
        long sampleWeight,
        ObjectNode jsonFields) {

    public ThreadInfo threadInfo() {
        return new ThreadInfo(thread.osThreadId(), thread.javaThreadId(), thread.name());
    }
}
