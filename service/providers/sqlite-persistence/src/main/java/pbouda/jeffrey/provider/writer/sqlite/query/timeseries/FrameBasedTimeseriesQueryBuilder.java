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

package pbouda.jeffrey.provider.writer.sqlite.query.timeseries;

import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.writer.sqlite.query.SQLParts;
import pbouda.jeffrey.sql.SQLBuilder;

import java.util.List;

import static pbouda.jeffrey.sql.SQLBuilder.*;

public class FrameBasedTimeseriesQueryBuilder implements TimeseriesQueryBuilder {

    private final SQLBuilder innerBuilder = new SQLBuilder();

    public FrameBasedTimeseriesQueryBuilder(String profileId, Type eventType, boolean useWeight) {
        String valueType = useWeight ? "events.weight" : "events.samples";
        innerBuilder
                .addColumn("CONCAT((events.timestamp_from_start / 1000), ',', sum(" + valueType + ")) AS pair")
                .addColumn("stacktraces.stacktrace_id")
                .addColumn("stacktraces.frames")
                .from("events")
                .where(SQLParts.profileAndType(profileId, eventType))
                .join("stacktraces", and(
                        eq("events.profile_id", c("stacktraces.profile_id")),
                        eq("events.stacktrace_id", c("stacktraces.stacktrace_id"))))
                .groupBy("(events.timestamp_from_start / 1000)", "stacktraces.stacktrace_id")
                .orderBy("stacktraces.stacktrace_id");
    }

    @Override
    public FrameBasedTimeseriesQueryBuilder withTimeRange(RelativeTimeRange timeRange) {
        if (timeRange != null) {
            innerBuilder.merge(SQLParts.timeRange(timeRange));
        }
        return this;
    }

    @Override
    public FrameBasedTimeseriesQueryBuilder withStacktraceTypes(List<StacktraceType> stacktraceTypes) {
        if (stacktraceTypes != null && !stacktraceTypes.isEmpty()) {
            innerBuilder.merge(SQLParts.stacktraceTypesFilterOnly(stacktraceTypes));
        }
        return this;
    }

    @Override
    public FrameBasedTimeseriesQueryBuilder withStacktraceTags(List<StacktraceTag> stacktraceTags) {
        if (stacktraceTags != null && !stacktraceTags.isEmpty()) {
            innerBuilder.merge(SQLParts.stacktraceTags(stacktraceTags));
        }
        return this;
    }

    @Override
    public String build() {
        String innerQuery = innerBuilder.build();
        return "SELECT GROUP_CONCAT(pair, ';') AS event_values, stacktrace_id, frames  FROM (" + innerQuery + ") GROUP BY stacktrace_id";
    }
}
