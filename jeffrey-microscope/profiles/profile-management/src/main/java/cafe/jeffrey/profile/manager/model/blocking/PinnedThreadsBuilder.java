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

package cafe.jeffrey.profile.manager.model.blocking;

import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Collects the longest {@code jdk.VirtualThreadPinned} incidents in a bounded min-heap,
 * returned longest-first.
 */
public class PinnedThreadsBuilder implements RecordBuilder<GenericRecord, List<PinnedThreadEntry>> {

    private static final String EVENT_THREAD_FIELD = "eventThread";

    private final int maxEntries;
    private final PriorityQueue<PinnedThreadEntry> longest =
            new PriorityQueue<>(Comparator.comparingLong(PinnedThreadEntry::durationNanos));

    public PinnedThreadsBuilder(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public void onRecord(GenericRecord record) {
        Duration duration = record.duration();
        long durationNanos = duration == null ? 0 : duration.toNanos();
        String thread = Json.readString(record.jsonFields(), EVENT_THREAD_FIELD);

        longest.offer(new PinnedThreadEntry(thread, durationNanos));
        if (longest.size() > maxEntries) {
            longest.poll();
        }
    }

    @Override
    public List<PinnedThreadEntry> build() {
        List<PinnedThreadEntry> ordered = new ArrayList<>(longest);
        ordered.sort(Comparator.comparingLong(PinnedThreadEntry::durationNanos).reversed());
        return ordered;
    }
}
