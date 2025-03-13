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

package pbouda.jeffrey.provider.api.query;

import pbouda.jeffrey.common.ThreadInfo;
import pbouda.jeffrey.common.model.profile.StacktraceTag;
import pbouda.jeffrey.common.model.profile.StacktraceType;

import java.time.Duration;
import java.util.List;

public interface EventStreamer {

    default EventStreamer groupByStacktraces() {
        return this;
    }

    /**
     * Limit the query to the specified types of stacktraces.
     *
     * @param types types of stacktraces
     * @return query builder with limited stacktraces
     */
    default EventStreamer stacktraces(List<StacktraceType> types) {
        return this;
    }

    /**
     * It takes all types of stacktraces.
     *
     * @return query builder with limited stacktraces
     */
    default EventStreamer stacktraces() {
        return this.stacktraces(List.of());
    }

    /**
     * Limit the query to the specified tags of stacktraces.
     *
     * @param tags tags of stacktraces
     * @return query builder with limited stacktraces
     */
    default EventStreamer stacktraceTags(List<StacktraceTag> tags) {
        return this;
    }

    default EventStreamer threads(boolean threadsIncluded, ThreadInfo threadInfo) {
        return this;
    }

    default EventStreamer withThreads() {
        return threads(true, null);
    }

    default EventStreamer withEventTypeInfo() {
        return this;
    }

    default EventStreamer withJsonFields() {
        return this;
    }

    default EventStreamer from(Duration timestamp) {
        return this;
    }

    default EventStreamer until(Duration timestamp) {
        return this;
    }

    RecordQuery build();
}
