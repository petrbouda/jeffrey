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
 * What a list of traces can be ordered by — today, the traces an attribute search matched.
 * <p>
 * An enum rather than a column name off the request, because the value is interpolated into the
 * {@code ORDER BY} of a statement whose other values are bound: a column cannot be a bind parameter,
 * so the only safe form is a closed set the caller picks from. Each constant carries the expression
 * it sorts on, which is the one place a column name for this appears at all.
 */
public enum TraceSortField {

    DURATION("duration"),
    START("start_timestamp"),
    SPAN_COUNT("span_count"),
    ERROR_COUNT("error_count");

    private final String column;

    TraceSortField(String column) {
        this.column = column;
    }

    /** The column this sorts on. Never caller-supplied — see the type's own note. */
    public String column() {
        return column;
    }
}
