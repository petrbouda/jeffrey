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
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.microscope.model.Type;

import java.time.Duration;

/**
 * Accumulates headline I/O totals in a single pass over a stream scoped to one {@link IoKind}.
 */
public class IoOverviewBuilder implements RecordBuilder<GenericRecord, IoOverview> {

    private long bytesRead;
    private long bytesWritten;
    private long opCount;
    private long slowestNanos;
    private String slowestTarget;
    private boolean hasEvents;

    @Override
    public void onRecord(GenericRecord record) {
        Type type = record.type();
        ObjectNode fields = record.jsonFields();
        long bytes = IoEventFields.bytes(type, fields);

        if (IoEventFields.isRead(type)) {
            bytesRead += bytes;
        } else {
            bytesWritten += bytes;
        }
        opCount++;
        hasEvents = true;

        Duration duration = record.duration();
        long durationNanos = duration == null ? 0 : duration.toNanos();
        if (durationNanos > slowestNanos) {
            slowestNanos = durationNanos;
            slowestTarget = IoEventFields.target(type, fields);
        }
    }

    @Override
    public IoOverview build() {
        return new IoOverview(bytesRead, bytesWritten, opCount, slowestNanos, slowestTarget, hasEvents);
    }
}
