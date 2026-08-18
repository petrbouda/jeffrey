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

package cafe.jeffrey.profile.manager;

/**
 * How a trace or span id crosses the wire.
 * <p>
 * As a 16-char hex string, never as a JSON number: the ids are 64-bit, which exceeds JavaScript's
 * safe integer range, so a numeric type would silently round them in the browser. Hex is also how
 * every other tracer renders them, so an id copied out of Jeffrey is recognisable elsewhere.
 * <p>
 * One definition rather than one per manager: two views that spell the same id differently cannot
 * be linked to each other, and the bug does not show up until someone tries.
 */
public abstract class TraceIds {

    private TraceIds() {
    }

    public static String hex(long id) {
        return String.format("%016x", id);
    }
}
