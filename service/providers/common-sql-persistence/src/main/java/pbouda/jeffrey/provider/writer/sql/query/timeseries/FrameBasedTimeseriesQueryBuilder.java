/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.writer.sql.query.timeseries;

import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.ThreadInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.writer.sql.query.SQLFormatter;
import pbouda.jeffrey.sql.SQLBuilder;

import java.util.List;

public class FrameBasedTimeseriesQueryBuilder implements TimeseriesQueryBuilder {

    private final TimeseriesQueryBuilder innerQueryBuilder;

    public FrameBasedTimeseriesQueryBuilder(SQLFormatter sqlFormatter, String profileId, Type eventType, boolean useWeight) {
        String valueType = useWeight ? "events.weight" : "events.samples";
        SQLBuilder innerBuilder = new SQLBuilder()
                .addColumn("CONCAT((events.start_timestamp_from_beginning / 1000), ',', sum(" + valueType + ")) AS pair")
                .from("events")
                .where(sqlFormatter.profileAndType(profileId, eventType))
                .groupBy("(events.start_timestamp_from_beginning / 1000)", "stacktraces.stacktrace_hash")
                .orderBy("stacktraces.stacktrace_hash")
                .merge(sqlFormatter.stacktraces());

        this.innerQueryBuilder = new AbstractTimeseriesQueryBuilder(sqlFormatter, innerBuilder, true) {
        };
    }

    @Override
    public TimeseriesQueryBuilder withTimeRange(RelativeTimeRange timeRange) {
        innerQueryBuilder.withTimeRange(timeRange);
        return this;
    }

    @Override
    public TimeseriesQueryBuilder withSpecifiedThread(ThreadInfo threadInfo) {
        innerQueryBuilder.withSpecifiedThread(threadInfo);
        return this;
    }

    @Override
    public TimeseriesQueryBuilder withStacktraceTypes(List<StacktraceType> stacktraceTypes) {
        innerQueryBuilder.withStacktraceTypes(stacktraceTypes);
        return this;
    }

    @Override
    public TimeseriesQueryBuilder withStacktraceTags(List<StacktraceTag> stacktraceTags) {
        innerQueryBuilder.withStacktraceTags(stacktraceTags);
        return this;
    }

    @Override
    public TimeseriesQueryBuilder merge(SQLBuilder builder) {
        this.innerQueryBuilder.merge(builder);
        return this;
    }

    @Override
    public String build() {
        String innerQuery = innerQueryBuilder.build();
        return "SELECT GROUP_CONCAT(pair, ';') AS event_values, stacktrace_id, frames  FROM (" + innerQuery + ") GROUP BY stacktrace_id";
    }
}
