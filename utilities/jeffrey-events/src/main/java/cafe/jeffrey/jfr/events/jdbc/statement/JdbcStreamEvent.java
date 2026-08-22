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

package cafe.jeffrey.jfr.events.jdbc.statement;

import jdk.jfr.Category;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * A query consumed as a stream, committed when the stream closes rather than when the statement
 * returns. That deferred commit may run after the enclosing span's binding is gone — or inside
 * someone else's — so the emitter stamps eagerly with
 * {@link cafe.jeffrey.jfr.events.trace.Tracer#stamp Tracer.stamp} at construction;
 * {@link cafe.jeffrey.jfr.events.trace.AbstractTracedEvent#commitSpan() commitSpan()} never
 * re-stamps an event that already carries identity.
 */
@Name(JdbcStreamEvent.NAME)
@Label("JDBC Stream Statement")
@Category({"Application", "JDBC"})
public class JdbcStreamEvent extends JdbcQueryEvent {

    public static final String NAME = "jeffrey.JdbcStream";

    public JdbcStreamEvent(String name, String group) {
        super(name, group);
    }
}
