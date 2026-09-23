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
