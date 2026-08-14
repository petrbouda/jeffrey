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

package cafe.jeffrey.jfr.events.jdbc.pool;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.StackTrace;

/**
 * Anything the connection pool reports.
 * <p>
 * Deliberately not a traced event. The pool reports after the fact — an elapsed time handed to a
 * callback, or a periodic sample — with no interval of its own for JFR to time, so a pool event
 * turned into a span would draw as a zero-width bar in a waterfall while its own field claimed the
 * 200 ms it actually took. Whatever waited for the connection is the span; this stays a measurement
 * hanging off it.
 */
@Category({"Application", "JDBC Pool"})
@StackTrace(false)
public abstract class JdbcPoolEvent extends Event {

    @Label("Pool Name")
    public String poolName;
}
