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

package pbouda.jeffrey.shared.common.model;

public enum StacktraceTag {
    EXCLUDE_IDLE(0, false),
    UNSAFE_ALLOCATION(1, true);

    private static final StacktraceTag[] VALUES = values();

    private final int id;
    private final boolean includes;

    /**
     * @param id       ID of the tag to optimize the space in DB and avoid storing duplicated strings
     * @param includes tag includes or excludes records from the database (mapped to IN or NOT IN clause)
     */
    StacktraceTag(int id, boolean includes) {
        this.id = id;
        this.includes = includes;
    }

    public int id() {
        return id;
    }

    public boolean includes() {
        return includes;
    }
}
