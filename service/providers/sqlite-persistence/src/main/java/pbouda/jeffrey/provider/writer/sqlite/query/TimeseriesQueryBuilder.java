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
import pbouda.jeffrey.sql.criteria.SQLBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static pbouda.jeffrey.sql.criteria.SQLBuilder.*;

public class TimeseriesQueryBuilder implements QueryBuilder {

    private final boolean needFrames;
    private boolean useWeight = false;
    private String profileId;
    private Type eventType;
    private RelativeTimeRange timeRange;
    private boolean eventFieldsIncluded = false;

    private List<StacktraceType> stacktraceTypes;
    private List<StacktraceTag> stacktraceTags;

    public TimeseriesQueryBuilder(boolean needFrames) {
        this.needFrames = needFrames;
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

    public TimeseriesQueryBuilder withJsonFields(boolean includeJsonFields) {
        this.eventFieldsIncluded = includeJsonFields;
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
        if (needFrames) {
            return buildFrameBasedQuery();
        } else {
            return buildSimpleQuery();
        }
    }

    private String buildSimpleQuery() {
        SQLBuilder criteria = new SQLBuilder()
                .addColumn("(events.timestamp_from_start / 1000) AS seconds")
                .addColumn("sum(" + (useWeight ? "events.weight" : "events.samples") + ") as value");

        // Conditionally add event fields if requested
        if (eventFieldsIncluded) {
            criteria.addColumn("GROUP_CONCAT(DISTINCT events.event_id) as event_ids");
        }

        criteria.from("events");

        addJoins(criteria);
        addWhereConditions(criteria);

        criteria.groupBy("seconds").orderBy("seconds");

        return criteria.build();
    }

    private String buildFrameBasedQuery() {
        String valueType = useWeight ? "events.weight" : "events.samples";

        // Inner query
        SQLBuilder innerCriteria = new SQLBuilder();
        innerCriteria.addColumn("CONCAT((events.timestamp_from_start / 1000), ',', sum(" + valueType + ")) AS pair")
                .addColumn("stacktraces.stacktrace_id")
                .addColumn("stacktraces.frames")
                .from("events");

        // Always include stacktraces for frame-based queries
        innerCriteria.join("stacktraces",
                and(eq("events.profile_id", c("stacktraces.profile_id")),
                        eq("events.stacktrace_id", c("stacktraces.stacktrace_id"))));

        addStacktraceTagsJoin(innerCriteria);
        addWhereConditions(innerCriteria);

        innerCriteria.groupBy("(events.timestamp_from_start / 1000)", "stacktraces.stacktrace_id")
                .orderBy("stacktraces.stacktrace_id");

        // Outer query
        String innerQuery = innerCriteria.build();
        return "SELECT GROUP_CONCAT(pair, ';') AS event_values, stacktrace_id, frames  FROM (" + innerQuery + ") GROUP BY stacktrace_id";
    }

    private void addJoins(SQLBuilder criteria) {
        if (stacktraceTypes != null) {
            criteria.join("stacktraces",
                    and(eq("events.profile_id", c("stacktraces.profile_id")),
                            eq("events.stacktrace_id", c("stacktraces.stacktrace_id"))));
        }

        addStacktraceTagsJoin(criteria);
    }

    private void addStacktraceTagsJoin(SQLBuilder criteria) {
        if (stacktraceTags != null) {
            criteria.leftJoin("stacktrace_tags tags",
                    and(eq("events.profile_id", c("tags.profile_id")),
                            eq("events.stacktrace_id", c("tags.stacktrace_id"))));
        }
    }

    private void addWhereConditions(SQLBuilder criteria) {
        // Base conditions
        criteria.where(eq("events.profile_id", l(profileId)))
                .and(eq("events.event_type", l(eventType.code())));

        // Stacktrace types
        if (stacktraceTypes != null) {
            List<Integer> typeIds = stacktraceTypes.stream()
                    .map(StacktraceType::id)
                    .toList();

            criteria.and(inInts("stacktraces.type_id", typeIds));
        }

        // Stacktrace tags
        if (stacktraceTags != null) {
            addStacktraceTagConditions(criteria);
        }

        // Time range
        if (timeRange != null) {
            criteria.and(gte("events.timestamp_from_start", l(timeRange.start().toMillis())))
                    .and(lt("events.timestamp_from_start", l(timeRange.end().toMillis())));
        }
    }

    private void addStacktraceTagConditions(SQLBuilder criteria) {
        Map<Boolean, List<StacktraceTag>> partitioned = stacktraceTags.stream()
                .collect(Collectors.partitioningBy(StacktraceTag::includes));

        List<StacktraceTag> included = partitioned.get(true);
        if (!included.isEmpty()) {
            List<Integer> includedIds = included.stream()
                    .map(StacktraceTag::id)
                    .toList();

            criteria.and(inInts("tags.tag_id", includedIds));
        }

        List<StacktraceTag> excluded = partitioned.get(false);
        if (!excluded.isEmpty()) {
            List<Integer> excludedIds = excluded.stream()
                    .map(StacktraceTag::id)
                    .toList();

            criteria.and(notInOrNullInts("tags.tag_id", excludedIds));
        }
    }
}
