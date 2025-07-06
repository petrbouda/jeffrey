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
import pbouda.jeffrey.common.model.ThreadInfo;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.writer.sqlite.query.SQLParts;
import pbouda.jeffrey.sql.SQLBuilder;

import java.util.List;

public abstract class AbstractTimeseriesQueryBuilder implements TimeseriesQueryBuilder {

    private final SQLBuilder builder;
    private final boolean includeStacktraces;

    public AbstractTimeseriesQueryBuilder(SQLBuilder baseBuilder, boolean includeStacktraces) {
        this.builder = baseBuilder;
        this.includeStacktraces = includeStacktraces;
    }

    @Override
    public TimeseriesQueryBuilder withTimeRange(RelativeTimeRange timeRange) {
        if (timeRange != null) {
            builder.merge(SQLParts.timeRange(timeRange));
        }
        return this;
    }

    @Override
    public TimeseriesQueryBuilder withSpecifiedThread(ThreadInfo threadInfo) {
        if (threadInfo != null) {
            builder.merge(SQLParts.threads())
                    .merge(SQLParts.threadInfo(threadInfo));
        }
        return this;
    }

    @Override
    public TimeseriesQueryBuilder withStacktraceTypes(List<StacktraceType> stacktraceTypes) {
        if (stacktraceTypes != null && !stacktraceTypes.isEmpty()) {
            builder.merge(SQLParts.stacktraceTypes(stacktraceTypes, includeStacktraces));
        }
        return this;
    }

    @Override
    public TimeseriesQueryBuilder withStacktraceTags(List<StacktraceTag> stacktraceTags) {
        if (stacktraceTags != null && !stacktraceTags.isEmpty()) {
            builder.merge(SQLParts.stacktraceTags(stacktraceTags));
        }
        return this;
    }

    @Override
    public String build() {
        return builder.build();
    }
}
