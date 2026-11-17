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
