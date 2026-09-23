/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
