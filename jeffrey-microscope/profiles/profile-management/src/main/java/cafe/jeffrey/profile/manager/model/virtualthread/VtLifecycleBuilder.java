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

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the virtual-thread lifecycle timeline from {@code jdk.VirtualThreadStart} and
 * {@code jdk.VirtualThreadEnd}: per-second creation and completion counts, plus a derived
 * running <em>live</em> count (cumulative starts minus ends) that exposes virtual-thread leaks
 * and runaway spawning. These events are disabled by default, so the result is empty unless the
 * recording explicitly enabled them.
 */
public class VtLifecycleBuilder implements RecordBuilder<GenericRecord, VtLifecycleBuilder.Result> {

    public record Result(TimeseriesData timeline, long started, long ended, long peakLive) {
    }

    private static final String STARTED_SERIES = "Started";
    private static final String ENDED_SERIES = "Ended";
    private static final String LIVE_SERIES = "Live";

    private final LongLongHashMap startsSeries;
    private final LongLongHashMap endsSeries;
    private long started;
    private long ended;

    public VtLifecycleBuilder(RelativeTimeRange timeRange) {
        this.startsSeries = TimeseriesUtils.initWithZeros(timeRange);
        this.endsSeries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        long seconds = record.timestampFromStart().toSeconds();
        if (EventTypeName.VIRTUAL_THREAD_START.equals(record.type().code())) {
            startsSeries.addToValue(seconds, 1);
            started++;
        } else if (EventTypeName.VIRTUAL_THREAD_END.equals(record.type().code())) {
            endsSeries.addToValue(seconds, 1);
            ended++;
        }
    }

    @Override
    public Result build() {
        SingleSerie startsSerie = TimeseriesUtils.buildSerie(STARTED_SERIES, startsSeries);
        SingleSerie endsSerie = TimeseriesUtils.buildSerie(ENDED_SERIES, endsSeries);

        // The two series share the same sorted second keys; prefix-sum (starts - ends) gives the live count.
        List<List<Long>> startsData = startsSerie.data();
        List<List<Long>> endsData = endsSerie.data();
        List<List<Long>> liveData = new ArrayList<>(startsData.size());
        long running = 0;
        long peakLive = 0;
        for (int i = 0; i < startsData.size(); i++) {
            running += startsData.get(i).get(1) - endsData.get(i).get(1);
            peakLive = Math.max(peakLive, running);
            liveData.add(List.of(startsData.get(i).get(0), Math.max(0, running)));
        }

        TimeseriesData timeline = new TimeseriesData(startsSerie, endsSerie, new SingleSerie(LIVE_SERIES, liveData));
        return new Result(timeline, started, ended, peakLive);
    }
}
