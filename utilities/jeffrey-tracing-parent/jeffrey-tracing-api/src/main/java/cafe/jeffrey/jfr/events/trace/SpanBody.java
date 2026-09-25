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

package cafe.jeffrey.jfr.events.trace;

/**
 * The work a span or a traced event covers, with its own throwable type so a checked exception
 * propagates through {@link Tracer#callChecked}, {@link Tracer#inSpanOf}, {@link Tracer#reenter},
 * {@link Tracer#continueIn} and {@link TracedEvents#emit} unchanged: a body that throws
 * {@code IOException} makes the call throw {@code IOException}, neither wrapped nor erased to
 * {@code Exception}.
 *
 * @param <R> the result of the work
 * @param <X> what the work may throw
 */
@FunctionalInterface
public interface SpanBody<R, X extends Throwable> {

    R call() throws X;
}
