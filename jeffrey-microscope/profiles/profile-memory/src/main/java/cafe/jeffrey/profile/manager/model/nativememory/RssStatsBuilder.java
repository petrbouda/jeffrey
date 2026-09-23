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

package cafe.jeffrey.profile.manager.model.nativememory;

import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

/**
 * Computes first/last/peak resident-set-size stats from a time-ordered stream of
 * {@code jdk.ResidentSetSize} events.
 *
 * @param firstRss RSS at the first sample (0 when no samples)
 * @param lastRss  RSS at the last sample (0 when no samples)
 * @param peakRss  highest {@code peak} value reported across the recording
 */
public class RssStatsBuilder implements RecordBuilder<GenericRecord, RssStatsBuilder.RssStats> {

    public record RssStats(long firstRss, long lastRss, long peakRss) {
    }

    private static final String SIZE_FIELD = "size";
    private static final String PEAK_FIELD = "peak";

    private long firstRss = -1;
    private long lastRss;
    private long peakRss;

    @Override
    public void onRecord(GenericRecord record) {
        long size = Json.readLong(record.jsonFields(), SIZE_FIELD);
        if (size < 0) {
            return;
        }
        if (firstRss < 0) {
            firstRss = size;
        }
        lastRss = size;
        peakRss = Math.max(peakRss, Math.max(size, Json.readLong(record.jsonFields(), PEAK_FIELD)));
    }

    @Override
    public RssStats build() {
        return new RssStats(Math.max(0, firstRss), lastRss, peakRss);
    }
}
