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

package cafe.jeffrey.profile.manager.model.io;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.microscope.model.Type;

import java.time.Duration;

/**
 * Running totals for one endpoint — op count, bytes and total/max duration.
 */
final class IoEndpointAccumulator {

    private long opCount;
    private long bytes;
    private long totalNanos;
    private long maxNanos;

    void record(GenericRecord record) {
        Type type = record.type();
        ObjectNode fields = record.jsonFields();

        Duration duration = record.duration();
        long durationNanos = duration == null ? 0 : duration.toNanos();

        opCount++;
        bytes += IoEventFields.bytes(type, fields);
        totalNanos += durationNanos;
        maxNanos = Math.max(maxNanos, durationNanos);
    }

    IoEndpoint toEndpoint(String target) {
        return new IoEndpoint(target, opCount, bytes, totalNanos, maxNanos);
    }
}
