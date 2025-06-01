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

package pbouda.jeffrey.provider.writer.sqlite.query;

import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;

import java.util.List;

public class TimeseriesQueryBuilder implements QueryBuilder {

    private static final String VALUE_TYPE_SAMPLES = "events.samples";
    private static final String VALUE_TYPE_WEIGHT = "events.weight";

    private static final String EMPTY = "";

    //language=sql
    private static final String TIME_RANGE_TOKEN = """
            AND events.timestamp_from_start >= <time-range-start>
                AND events.timestamp_from_start < <time-range-end>""";

    //language=sql
    private static final String STACKTRACES_TOKEN = """
            INNER JOIN stacktraces
                    ON events.profile_id = stacktraces.profile_id
                           AND events.stacktrace_id = stacktraces.stacktrace_id""";

    //language=sql
    private static final String STACKTRACE_TAGS_TOKEN = """
            LEFT JOIN stacktrace_tags tags
                    ON events.profile_id = tags.profile_id
                            AND events.stacktrace_id = tags.stacktrace_id""";

    //language=sql
    private static final String SIMPLE_TIMESERIES_QUERY = """
            SELECT (events.timestamp_from_start / 1000) AS seconds, sum(<value-type>) as value
                FROM events
                <stacktraces-token>
                <stacktrace-tags-token>
                <where-token>
                <time-range>
                GROUP BY seconds ORDER BY seconds""";

    //language=sql
    private static final String FRAME_BASED_TIMESERIES_QUERY = """
            SELECT GROUP_CONCAT(pair, ';') AS event_values, stacktrace_id, frames  FROM (
                SELECT
                    CONCAT((events.timestamp_from_start / 1000), ',', sum(<value-type>)) AS pair,
                    stacktraces.stacktrace_id,
                    stacktraces.frames
                FROM events
                <stacktraces-token>
                <stacktrace-tags-token>
                <where-token>
                <time-range>
                GROUP BY(events.timestamp_from_start / 1000), stacktraces.stacktrace_id ORDER BY stacktraces.stacktrace_id
            ) GROUP BY stacktrace_id""";

    private final String selectedQuery;
    private boolean useWeight = false;
    private String profileId;
    private Type eventType;
    private RelativeTimeRange timeRange;

    private List<StacktraceType> stacktraceTypes;
    private List<StacktraceTag> stacktraceTags;

    public TimeseriesQueryBuilder(boolean needFrames) {
        if (needFrames) {
            // always includes stacktraces
            this.selectedQuery = FRAME_BASED_TIMESERIES_QUERY
                    .replace("<stacktraces-token>", STACKTRACES_TOKEN);
        } else {
            this.selectedQuery = SIMPLE_TIMESERIES_QUERY;
        }
    }

    public TimeseriesQueryBuilder withWeight(boolean useWeight) {
        this.useWeight = useWeight;
        return this;
    }

    public TimeseriesQueryBuilder withProfileId(String profileId) {
        this.profileId = profileId;
        return this;
    }

    public TimeseriesQueryBuilder withEventType(Type eventType) {
        this.eventType = eventType;
        return this;
    }

    public TimeseriesQueryBuilder withTimeRange(RelativeTimeRange timeRange) {
        this.timeRange = timeRange;
        return this;
    }

    public TimeseriesQueryBuilder filterStacktraceTypes(List<StacktraceType> stacktraceTypes) {
        if (stacktraceTypes != null && !stacktraceTypes.isEmpty()) {
            this.stacktraceTypes = stacktraceTypes;
        }
        return this;
    }

    public TimeseriesQueryBuilder filterStacktraceTags(List<StacktraceTag> stacktraceTags) {
        if (stacktraceTags != null && !stacktraceTags.isEmpty()) {
            this.stacktraceTags = stacktraceTags;
        }
        return this;
    }

    @Override
    public String build() {
        String query = selectedQuery;
        query = useWeight
                ? query.replace("<value-type>", VALUE_TYPE_WEIGHT)
                : query.replace("<value-type>", VALUE_TYPE_SAMPLES);

        if (stacktraceTypes != null) {
            query = query.replace("<stacktraces-token>", STACKTRACES_TOKEN);
        } else {
            query = query.replace("<stacktraces-token>", EMPTY);
        }

        if (stacktraceTags != null) {
            query = query.replace("<stacktrace-tags-token>", STACKTRACE_TAGS_TOKEN);
        } else {
            query = query.replace("<stacktrace-tags-token>", EMPTY);
        }

        StringBuilder whereClause = new StringBuilder();
        whereClause.append(
                "WHERE events.profile_id = '" + profileId + "' AND events.event_type = '" + eventType.code() + "'");

        QueryUtils.appendStacktraceTypes(whereClause, stacktraceTypes);

        QueryUtils.appendStacktraceTags(whereClause, stacktraceTags);

        query = query.replace("<where-token>", whereClause.toString());

        if (timeRange != null) {
            String timeRangeToken = TIME_RANGE_TOKEN
                    .replace("<time-range-start>", String.valueOf(timeRange.start().toMillis()))
                    .replace("<time-range-end>", String.valueOf(timeRange.end().toMillis()));

            query = query.replace("<time-range>", timeRangeToken);
        }

        return query;
    }
}
