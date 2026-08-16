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
import cafe.jeffrey.jfr.events.trace.SpanName;
import cafe.jeffrey.jfr.events.trace.SpanStatus;
import jdk.jfr.Description;
import jdk.jfr.Label;

/**
 * A statement run against a database, which is a span whose shape is already filled in by the time
 * it starts: the statement's own label is the span name, and a statement is always a call out to
 * something else. Only the outcome is left, and {@link #failed(Throwable)} settles that.
 * <p>
 * The declared template is the identity, {@code {name}}: a statement names itself, and the label is
 * assigned at construction, so it is recorded whatever the commit path. The declaration is there so
 * that every span type this library ships carries its convention in the recording — an event type
 * with no {@code @SpanName} at all is one that has no naming convention, not one whose rule lives
 * somewhere else. No {@code @SpanOutcome} accompanies it: a statement records no outcome code to
 * judge — {@link #failed(Throwable)} writes the span status directly, and a recorded {@code ERROR}
 * outranks any declared semantics anyway.
 */
@SpanName("{name}")
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

    /**
     * Records that the statement threw, which is what makes a failed statement count as an error in
     * the trace it belongs to.
     */
    public void failed(Throwable failure) {
        this.status = SpanStatus.ERROR.name();
        this.errorType = failure.getClass().getName();
    }
}
