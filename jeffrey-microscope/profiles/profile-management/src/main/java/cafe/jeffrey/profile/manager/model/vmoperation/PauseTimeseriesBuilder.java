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

package cafe.jeffrey.profile.manager.model.vmoperation;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.time.Duration;
import java.util.List;

/**
 * Sums safepoint VM-operation pause time per second from {@code jdk.ExecuteVMOperation}, building a
 * single "Safepoint Pause Time" series so the VM Operations page can plot stop-the-world pause time
 * over the recording. Non-safepoint operations are ignored.
 */
public class PauseTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String SERIES_NAME = "Safepoint Pause Time";
    private static final String SAFEPOINT_FIELD = "safepoint";

    private final LongLongHashMap values;

    public PauseTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.values = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        if (!Json.readBoolean(fields, SAFEPOINT_FIELD)) {
            return;
        }
        Duration duration = record.duration();
        if (duration == null) {
            return;
        }
        values.addToValue(record.timestampFromStart().toSeconds(), duration.toNanos());
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(List.of(TimeseriesUtils.buildSerie(SERIES_NAME, values)));
    }
}
