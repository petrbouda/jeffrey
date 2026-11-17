/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
