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
 * Groups file I/O events by their parent directory (the path up to the last {@code /}), accumulating
 * op count, bytes and total/max duration, ordered by descending bytes. Surfaces the hot folder
 * (a log directory, a data directory) behind file I/O. Reuses the {@link IoEndpoint} shape with the
 * directory as the target.
 */
public class IoDirectoriesBuilder implements RecordBuilder<GenericRecord, List<IoEndpoint>> {

    private static final String ROOT = "/";

    private static final class Accumulator {
        private long opCount;
        private long bytes;
        private long totalNanos;
        private long maxNanos;
    }

    private final Map<String, Accumulator> accumulatorsByDir = new HashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        Type type = record.type();
        ObjectNode fields = record.jsonFields();
        String directory = directoryOf(IoEventFields.filePath(fields));

        Duration duration = record.duration();
        long durationNanos = duration == null ? 0 : duration.toNanos();

        Accumulator accumulator = accumulatorsByDir.computeIfAbsent(directory, key -> new Accumulator());
        accumulator.opCount++;
        accumulator.bytes += IoEventFields.bytes(type, fields);
        accumulator.totalNanos += durationNanos;
        accumulator.maxNanos = Math.max(accumulator.maxNanos, durationNanos);
    }

    @Override
    public List<IoEndpoint> build() {
        List<IoEndpoint> result = new ArrayList<>(accumulatorsByDir.size());
        accumulatorsByDir.forEach((directory, accumulator) -> result.add(new IoEndpoint(
                directory,
                accumulator.opCount,
                accumulator.bytes,
                accumulator.totalNanos,
                accumulator.maxNanos)));
        result.sort(Comparator.comparingLong(IoEndpoint::bytes).reversed());
        return result;
    }

    private static String directoryOf(String path) {
        int slash = path.lastIndexOf('/');
        if (slash > 0) {
            return path.substring(0, slash);
        }
        if (slash == 0) {
            return ROOT;
        }
        return path;
    }
}
