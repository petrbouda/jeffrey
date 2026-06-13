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
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups I/O events by endpoint — socket peer ({@code host:port}) or file path — accumulating
 * op count, total bytes and total/max duration, ordered by descending bytes. The caller scopes the
 * event stream to either socket or file events, so the same builder serves both Top Peers and Files.
 */
public class IoEndpointsBuilder implements RecordBuilder<GenericRecord, List<IoEndpoint>> {

    private static final class Accumulator {
        private long opCount;
        private long bytes;
        private long totalNanos;
        private long maxNanos;
    }

    private final Map<String, Accumulator> accumulatorsByTarget = new HashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        Type type = record.type();
        ObjectNode fields = record.jsonFields();
        String target = IoEventFields.target(type, fields);

        Duration duration = record.duration();
        long durationNanos = duration == null ? 0 : duration.toNanos();

        Accumulator accumulator = accumulatorsByTarget.computeIfAbsent(target, key -> new Accumulator());
        accumulator.opCount++;
        accumulator.bytes += IoEventFields.bytes(type, fields);
        accumulator.totalNanos += durationNanos;
        accumulator.maxNanos = Math.max(accumulator.maxNanos, durationNanos);
    }

    @Override
    public List<IoEndpoint> build() {
        List<IoEndpoint> result = new ArrayList<>(accumulatorsByTarget.size());
        accumulatorsByTarget.forEach((target, accumulator) -> result.add(new IoEndpoint(
                target,
                accumulator.opCount,
                accumulator.bytes,
                accumulator.totalNanos,
                accumulator.maxNanos)));
        result.sort(Comparator.comparingLong(IoEndpoint::bytes).reversed());
        return result;
    }
}
