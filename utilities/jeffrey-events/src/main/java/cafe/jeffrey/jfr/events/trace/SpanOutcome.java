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

package cafe.jeffrey.jfr.events.trace;

import jdk.jfr.MetadataDefinition;
import jdk.jfr.Description;
import jdk.jfr.Label;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which of this event type's fields holds the operation's own outcome code, and how a
 * reader judges it — so failure detection travels <em>inside the recording</em>, the same way
 * {@link SpanName} carries the naming rule.
 * <p>
 * A span's status decides whether its trace counts as failed. An event committed through
 * {@link AbstractTracedEvent#commitSpan()} records a status of its own; one committed with plain
 * {@link jdk.jfr.Event#commit()} records {@code UNSET} even when its own code field plainly says
 * the operation failed. This annotation is how a reader closes that gap without knowing the event
 * type: it names the code field and the rule that turns the code into an outcome.
 * <p>
 * What no code can express is a span the instrumentation itself failed — an exchange that threw
 * and still answered 200. A recorded status of {@code ERROR} therefore always outranks the
 * declared semantics in Jeffrey's derivation.
 *
 * <h2>Wire format</h2>
 * The semantics names below are read out of recordings that outlive every version of this library.
 * They are frozen: never renamed, only added to. A reader finding a semantics value it does not
 * know must ignore the declaration — not fail — and fall back to the recorded status.
 */
@MetadataDefinition
@Label("Span Outcome")
@Description("Which event field holds the operation's own outcome code, and how a reader judges it")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface SpanOutcome {

    /**
     * A numeric code judged the HTTP way: {@code >= 400} is {@code ERROR}, anything below is
     * {@code UNSET} — a completed exchange, not a vouched-for one.
     */
    String HTTP_CODE = "HTTP_CODE";

    /**
     * A textual code judged the gRPC way: {@code "OK"} is {@code OK}, anything else is
     * {@code ERROR}.
     */
    String GRPC_CODE = "GRPC_CODE";

    /**
     * A success flag: {@code false} is {@code ERROR}, anything else is {@code UNSET}.
     */
    String BOOLEAN = "BOOLEAN";

    /**
     * The name of the event field holding the outcome code, matching {@code [A-Za-z0-9_]+}.
     */
    String from();

    /**
     * One of {@link #HTTP_CODE}, {@link #GRPC_CODE} or {@link #BOOLEAN}. A {@code String} element
     * rather than an enum on purpose: JFR's own metadata annotations carry only strings and
     * primitives, and these values are wire format either way.
     */
    String semantics();
}
