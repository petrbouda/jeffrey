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
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.writer.sql.query.SQLFormatter;
import pbouda.jeffrey.sql.SQLBuilder;

import java.util.List;

public abstract class AbstractTimeseriesQueryBuilder implements TimeseriesQueryBuilder {

    private final SQLFormatter sqlFormatter;
    private final SQLBuilder builder;
    private final boolean includeStacktraces;

    public AbstractTimeseriesQueryBuilder(SQLFormatter sqlFormatter, SQLBuilder baseBuilder, boolean includeStacktraces) {
        this.sqlFormatter = sqlFormatter;
        this.builder = baseBuilder;
        this.includeStacktraces = includeStacktraces;
    }

    @Override
    public TimeseriesQueryBuilder withTimeRange(RelativeTimeRange timeRange) {
        if (timeRange != null) {
            builder.merge(sqlFormatter.timeRange(timeRange));
        }
        return this;
    }

    @Override
    public TimeseriesQueryBuilder withSpecifiedThread(ThreadInfo threadInfo) {
        if (threadInfo != null) {
            builder.merge(sqlFormatter.threads())
                    .merge(sqlFormatter.threadInfo(threadInfo));
        }
        return this;
    }

    @Override
    public TimeseriesQueryBuilder withStacktraceTypes(List<StacktraceType> stacktraceTypes) {
        if (stacktraceTypes != null && !stacktraceTypes.isEmpty()) {
            builder.merge(sqlFormatter.stacktraceTypes(stacktraceTypes, includeStacktraces));
        }
        return this;
    }

    @Override
    public TimeseriesQueryBuilder withStacktraceTags(List<StacktraceTag> stacktraceTags) {
        if (stacktraceTags != null && !stacktraceTags.isEmpty()) {
            builder.merge(sqlFormatter.stacktraceTags(stacktraceTags));
        }
        return this;
    }

    @Override
    public TimeseriesQueryBuilder merge(SQLBuilder builder) {
        this.builder.merge(builder);
        return this;
    }

    @Override
    public String build() {
        return builder.build();
    }
}
