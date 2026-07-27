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

package cafe.jeffrey.profile.manager.model.io;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.model.Type;

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
