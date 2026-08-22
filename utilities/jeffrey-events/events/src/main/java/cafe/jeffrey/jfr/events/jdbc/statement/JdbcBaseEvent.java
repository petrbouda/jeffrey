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

import cafe.jeffrey.jfr.events.trace.AbstractTracedEvent;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.Span;
import jdk.jfr.Description;
import jdk.jfr.Label;

/**
 * A statement run against a database, which is a span whose shape is already filled in by the time
 * it starts: the statement's own label is the span name, and a statement is always a call out to
 * something else. Only the outcome is left, and {@link #failed(Throwable)} settles that.
 * <p>
 * The declared template is the identity, {@code {name}}: a statement names itself, and the label is
 * assigned at construction, so it is recorded whatever the commit path. The declaration is there so
 * that every span type this library ships carries its naming convention in the recording — an event
 * type with no {@code @Span} at all is one that has no naming convention, not one whose rule
 * lives somewhere else. The verdict is not declared, here or anywhere: {@link #failed(Throwable)}
 * writes the span status directly, which is the one way a failure is stated.
 */
@Span("{name}")
public abstract class JdbcBaseEvent extends AbstractTracedEvent {

    @Label("SQL Query")
    @Description("The SQL statement executed by the JDBC statement")
    public String sql;

    @Label("SQL Parameters")
    public String params;

    @Label("Label for Statement Grouping")
    public String group;

    @Label("Affected/Returned Rows")
    @Description("The number of affected/returned rows")
    public long rows;

    public JdbcBaseEvent(String name, String group) {
        this.name = name;
        this.group = group;
        this.kind = SpanKind.CLIENT.name();
    }
}
