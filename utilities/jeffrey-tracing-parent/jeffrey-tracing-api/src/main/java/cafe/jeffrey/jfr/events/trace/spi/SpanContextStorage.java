/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.jfr.events.trace.spi;

import cafe.jeffrey.jfr.events.trace.SpanBody;
import cafe.jeffrey.jfr.events.trace.SpanContext;

/**
 * Where {@link cafe.jeffrey.jfr.events.trace.Tracer} keeps the span in progress on the current
 * thread.
 * <p>
 * Everything else about tracing — minting ids, emitting span and scope events, stamping leaf
 * events — lives in the Tracer; an implementation of this interface only binds a context for the
 * duration of a body and answers which context is bound. Two exist, each in its own artifact:
 * <ul>
 *   <li>{@code jeffrey-tracing-scoped-value} — a {@code ScopedValue}, Java 25+</li>
 *   <li>{@code jeffrey-tracing-thread-local} — a {@code ThreadLocal}, Java 21+</li>
 * </ul>
 * The Tracer finds them through {@link java.util.ServiceLoader} and uses the one with the highest
 * {@link #priority()} among those the running JVM can load. The Spring Boot starter brings the
 * {@code ThreadLocal} one; a Java 25 application adds {@code jeffrey-tracing-scoped-value} to run
 * on {@code ScopedValue}, which then wins.
 * <p>
 * A binding never outlives its body: {@link #callWith} must leave the thread exactly as it found
 * it, whether the body returns or throws.
 */
public interface SpanContextStorage {

    /**
     * @return the context bound on the calling thread, or {@code null} when none is
     */
    SpanContext current();

    /**
     * Binds {@code context} on the calling thread, runs {@code body}, and restores whatever was
     * bound before — on the exception path too.
     */
    <R, X extends Throwable> R callWith(SpanContext context, SpanBody<? extends R, X> body) throws X;

    /**
     * Which implementation wins when several are on the class path; higher wins.
     */
    int priority();
}
