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

package cafe.jeffrey.provider.profile.api;

/**
 * Which column ranks a key's values.
 * <p>
 * {@link #TOTAL_TIME} is the default the value list opens with, because ranking by call count
 * answers a different question than the one being asked: a busy value and an expensive value are
 * rarely the same value, and it is the expensive one that is worth looking at.
 */
public enum TraceAttributeValueSortField {

    TOTAL_TIME("total_nanos"),
    TRACES("trace_count"),
    P50("p50_nanos"),
    P95("p95_nanos"),
    MAX("max_nanos"),
    ERRORS("error_traces"),
    VALUE("value");

    private final String column;

    TraceAttributeValueSortField(String column) {
        this.column = column;
    }

    /** The column this sorts on. Never caller-supplied: the enum is what keeps it out of the SQL. */
    public String column() {
        return column;
    }
}
