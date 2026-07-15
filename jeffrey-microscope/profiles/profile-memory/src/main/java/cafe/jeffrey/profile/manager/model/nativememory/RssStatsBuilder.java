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
