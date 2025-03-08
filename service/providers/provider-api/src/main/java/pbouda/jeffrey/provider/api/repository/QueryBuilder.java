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

package pbouda.jeffrey.provider.api.repository;

import pbouda.jeffrey.common.ThreadInfo;
import pbouda.jeffrey.common.model.profile.StacktraceTag;
import pbouda.jeffrey.common.model.profile.StacktraceType;

import java.time.Duration;
import java.util.List;

public interface QueryBuilder {

    /**
     * Limit the query to the specified types of stacktraces.
     *
     * @param types types of stacktraces
     * @return query builder with limited stacktraces
     */
    QueryBuilder stacktraces(List<StacktraceType> types);

    /**
     * It takes all types of stacktraces.
     *
     * @return query builder with limited stacktraces
     */
    default QueryBuilder stacktraces() {
        return this.stacktraces(List.of());
    }

    /**
     * Limit the query to the specified tags of stacktraces.
     *
     * @param tags tags of stacktraces
     * @return query builder with limited stacktraces
     */
    QueryBuilder stacktraceTags(List<StacktraceTag> tags);

    QueryBuilder threads(boolean threadsIncluded, ThreadInfo threadInfo);

    default QueryBuilder withThreads() {
        return threads(true, null);
    }

    QueryBuilder withEventTypeInfo();

    QueryBuilder withJsonFields();

    QueryBuilder from(Duration timestamp);

    QueryBuilder until(Duration timestamp);

    RecordQuery build();
}
