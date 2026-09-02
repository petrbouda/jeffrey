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
