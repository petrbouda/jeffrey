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

package cafe.jeffrey.profile.manager.model.virtualthread;

import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData.SubmitFailure;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects {@code jdk.VirtualThreadSubmitFailed} events — a virtual thread that could not be
 * submitted to its carrier pool (carrier-pool rejection, executor shutdown, …). Most recent first.
 */
public class VtSubmitFailedBuilder implements RecordBuilder<GenericRecord, List<SubmitFailure>> {

    private static final String EVENT_THREAD_FIELD = "eventThread";
    private static final String EXCEPTION_MESSAGE_FIELD = "exceptionMessage";

    private final int maxEntries;
    private final List<SubmitFailure> failures = new ArrayList<>();

    public VtSubmitFailedBuilder(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public void onRecord(GenericRecord record) {
        failures.add(new SubmitFailure(
                record.timestampFromStart().toMillis(),
                Json.readString(record.jsonFields(), EVENT_THREAD_FIELD),
                Json.readString(record.jsonFields(), EXCEPTION_MESSAGE_FIELD)));
    }

    @Override
    public List<SubmitFailure> build() {
        return failures.stream()
                .sorted(Comparator.comparingLong(SubmitFailure::timeOffsetMillis).reversed())
                .limit(maxEntries)
                .toList();
    }
}
