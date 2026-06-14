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

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

/**
 * The app-stop budget: frozen nanoseconds summed per second into two series — "Global STW" (whole-JVM
 * pauses: GC + safepoint VM operations) and "Local Stalls" (per-thread: monitor / park / pinning).
 * Sums <em>every</em> classified event (no duration threshold) so the band is complete even when the
 * per-event timeline is thresholded. Time-to-safepoint is excluded (see {@link StwCategory#stopBudget}).
 */
public class StwBudgetBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String GLOBAL_SERIES_NAME = "Global STW";
    private static final String LOCAL_SERIES_NAME = "Local Stalls";
    private static final long MILLIS_PER_SECOND = 1000;

    private final LongLongHashMap globalBySecond;
    private final LongLongHashMap localBySecond;

    public StwBudgetBuilder(RelativeTimeRange timeRange) {
        this.globalBySecond = TimeseriesUtils.initWithZeros(timeRange);
        this.localBySecond = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        StwEvent event = StwClassifier.classify(record);
        if (event == null) {
            return;
        }
        long second = event.timeOffsetMillis() / MILLIS_PER_SECOND;
        if (event.category().stopBudget()) {
            globalBySecond.addToValue(second, event.durationNanos());
        } else if (event.scope() == StwScope.LOCAL) {
            localBySecond.addToValue(second, event.durationNanos());
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie global = TimeseriesUtils.buildSerie(GLOBAL_SERIES_NAME, globalBySecond);
        SingleSerie local = TimeseriesUtils.buildSerie(LOCAL_SERIES_NAME, localBySecond);
        return new TimeseriesData(global, local);
    }
}
