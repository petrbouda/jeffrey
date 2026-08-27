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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records a method as a span, without the method saying so in its body.
 * <p>
 * {@link Tracer#run} and {@link Tracer#call} wrap work in a lambda, which is precise but visible:
 * the method has to be written around the tracing. This is the declarative half — the Jeffrey
 * agent weaves the same span around every annotated method, and the method itself is untouched:
 *
 * <pre>{@code
 * @Traced(name = "order.checkout", args = {"tier=gold"}, includeMethodArgs = {"orderId"})
 * public Receipt checkout(String orderId, Card card) { ... }
 * }</pre>
 * <p>
 * The span behaves exactly like a {@code Tracer.call} one: it nests under whatever span is in
 * progress on the thread, records as its own root when there is none, and a throw sets the status
 * to {@code ERROR} with the exception's type.
 *
 * <h2>What it takes to work</h2>
 * The annotation alone records nothing — it is read by the Jeffrey agent, which must be attached
 * with method tracing switched on:
 *
 * <pre>{@code
 * java -javaagent:jeffrey-agent.jar=tracing.enabled=true -jar app.jar
 * }</pre>
 * <p>
 * The agent weaves classes as they load, so it has to be attached at startup, and it needs Java 25
 * (the tracing runtime is built on {@code ScopedValue}). Without the agent the annotation is inert
 * documentation and the method runs exactly as written — nothing fails, nothing is recorded.
 * <p>
 * Not to be confused with {@link Span}, which declares the <em>naming convention of an event type</em>
 * inside the recording's metadata. This one marks a method to record.
 *
 * @see Tracer for the explicit form, and for connecting spans across threads
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Traced {

    /**
     * The span name, which must stay low-cardinality — it is what Jeffrey aggregates on, so it may
     * never contain an id, a user name or anything else that varies per call.
     * <p>
     * Left empty, the name is derived as {@code SimpleClassName.methodName}
     * ({@code OrderService.checkout}), which is stable by construction.
     */
    String name() default "";

    /**
     * What kind of work this is: {@code INTERNAL} for application logic, {@code CLIENT} for a call
     * out to something else, {@code SERVER} for handling an inbound request.
     */
    SpanKind kind() default SpanKind.INTERNAL;

    /**
     * Fixed attributes recorded on every call, as {@code "key=value"} entries — the split is on the
     * first {@code =}, and an entry without one is ignored. Use them for facts about the method
     * that a reader of the recording would otherwise have to look up in the source.
     */
    String[] args() default {};

    /**
     * The parameters to record, by name, keyed on the span by that same name — a list of what may
     * be recorded rather than of what may not. {@code "*"} records every parameter; it is not a
     * legal Java identifier, so it can never collide with one.
     * <p>
     * Left empty, nothing is captured. A name matching no parameter is ignored: the usual cause is
     * a class compiled without javac's {@code -parameters}, where the real names are {@code arg0},
     * {@code arg1} and so on.
     * <p>
     * Only values whose textual form is stable and intentional are recorded — text, numbers,
     * booleans, enums, {@code UUID}, {@code BigDecimal}/{@code BigInteger} and the
     * {@code java.time} value types. An arbitrary domain object is refused rather than passed
     * through {@code toString()}, because a recording is a file that gets uploaded, shared and
     * kept, and a record's generated {@code toString()} would put every one of its components in
     * there. {@link CapturableTypes} has the full list and the reasoning.
     * <p>
     * How a refusal shows depends on when it can be known. A parameter whose declared type can
     * never be capturable — {@code checkout(Card card)} — is dropped when the method is first
     * read, with a warning naming it, so asking for something you will not get is said out loud
     * once rather than discovered in a recording later. A parameter whose declaration decides
     * nothing — {@code put(Object key)} — is kept, and a value that turns out to be uncapturable
     * is recorded as {@link CapturableTypes#UNSUPPORTED_VALUE}. Under {@code "*"} an uncapturable
     * parameter is passed over silently: a sweep is not a request for any particular one.
     * <p>
     * Values are truncated to a maximum length, because an argument is a fact about a call rather
     * than a payload.
     */
    String[] includeMethodArgs() default {};
}
