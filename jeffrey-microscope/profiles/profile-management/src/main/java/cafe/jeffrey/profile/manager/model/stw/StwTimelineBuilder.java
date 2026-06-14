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

package cafe.jeffrey.profile.manager.model.stw;

import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects every pause source into one chronological list of {@link StwEvent}s, keeping only pauses at
 * least {@code minDurationNanos} long so the timeline payload stays bounded (the complete frozen-time
 * picture lives in {@link StwBudgetBuilder}, which sums everything).
 */
public class StwTimelineBuilder implements RecordBuilder<GenericRecord, List<StwEvent>> {

    private final long minDurationNanos;
    private final List<StwEvent> events = new ArrayList<>();

    public StwTimelineBuilder(long minDurationNanos) {
        if (minDurationNanos < 0) {
            throw new IllegalArgumentException("minDurationNanos must be non-negative: " + minDurationNanos);
        }
        this.minDurationNanos = minDurationNanos;
    }

    @Override
    public void onRecord(GenericRecord record) {
        StwEvent event = StwClassifier.classify(record);
        if (event != null && event.durationNanos() >= minDurationNanos) {
            events.add(event);
        }
    }

    @Override
    public List<StwEvent> build() {
        events.sort(Comparator.comparingLong(StwEvent::timeOffsetMillis)
                .thenComparingLong(StwEvent::durationNanos));
        return events;
    }
}
