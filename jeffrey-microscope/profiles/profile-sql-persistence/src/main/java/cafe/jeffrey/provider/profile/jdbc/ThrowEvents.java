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

package cafe.jeffrey.provider.profile.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.common.model.EventTypeName;

import java.util.List;

/**
 * The profile's throws, one row per throwable constructed — the reading every statement over the
 * throw events starts from, so the trace derivation and the Exceptions page count the same thing.
 * <p>
 * One throwable, one row takes the {@code distinct_throws} step, because JFR does not record these
 * events once each. Both are emitted from constructors ({@code jdk.jfr.internal.instrument
 * .ThrowableTracer}, woven into {@code Throwable} and {@code Error}), and the Error hook commits an
 * ExceptionThrown of its own beside its ErrorThrown. So constructing an Error emits three events —
 * two {@code jdk.JavaExceptionThrow} and one {@code jdk.JavaErrorThrow} — while an ordinary exception
 * emits one. Counted raw, every Error is three throws and appears twice over, once under each event
 * type, as two findings that are really one.
 * <p>
 * The de-duplication is by class rather than by instant: a class either extends Error or it does not,
 * so a class that produced any {@code jdk.JavaErrorThrow} keeps only those rows, and its
 * {@code jdk.JavaExceptionThrow} rows are the duplicate view of the same objects. Matching on the
 * timestamp instead would be wrong — the Error hook and the Throwable hook read the clock separately,
 * so the twins are not guaranteed to share a tick. OutOfMemoryError is the one Error the tracer
 * skips, emitting no {@code jdk.JavaErrorThrow} at all; it produces no error rows to anti-join
 * against, so its single event survives, which is the right answer for it too.
 * <p>
 * A recording with {@code jdk.JavaErrorThrow} disabled has nothing to de-duplicate against and its
 * errors stay double-counted. Nothing here can recover that — the duplicate is indistinguishable from
 * a second throw — and both event types travel together in every settings file that enables either.
 */
final class ThrowEvents {

    /**
     * The event types that record a throw. The reader tells an Error from an Exception by the event
     * type a row kept, not by guessing from the class name.
     */
    static final List<String> EVENT_TYPES =
            List.of(EventTypeName.JAVA_EXCEPTION_THROW, EventTypeName.JAVA_ERROR_THROW);

    /** The event type that marks a throw as an Error. */
    static final String ERROR_EVENT_TYPE = EventTypeName.JAVA_ERROR_THROW;

    /** Bound by {@link #params()}. */
    static final String EVENT_TYPES_PARAM = "exception_event_types";
    static final String ERROR_EVENT_TYPE_PARAM = "error_throw_event_type";

    /**
     * Three CTEs — {@code thrown}, {@code named}, {@code distinct_throws} — to follow a {@code WITH}.
     * {@code distinct_throws} carries {@code exception_id} (the raw row's {@code rowid}),
     * {@code event_type}, {@code start_timestamp}, {@code start_ms}, {@code start_us},
     * {@code thread_hash}, {@code stacktrace_hash}, {@code thrown_class} and {@code message}.
     * <p>
     * Rows with no thread are kept: a consumer that attributes throws to a thread joins on it and
     * drops them there.
     */
    //language=SQL
    static final String DISTINCT_THROWS_CTES = """
            thrown AS (
                SELECT
                    e.rowid                                       AS exception_id,
                    e.event_type                                  AS event_type,
                    e.start_timestamp                             AS start_timestamp,
                    COALESCE(e.start_timestamp_from_beginning, 0)  AS start_ms,
                    EPOCH_US(e.start_timestamp)                   AS start_us,
                    e.thread_hash                                 AS thread_hash,
                    e.stacktrace_hash                             AS stacktrace_hash,
                    <<rehydrated>>                                AS fields
                FROM events_raw e
                LEFT JOIN field_texts t ON t.text_hash = e.pooled_text_hash
                WHERE e.event_type IN (:exception_event_types)
            ),
            named AS (
                SELECT
                    x.exception_id,
                    x.event_type,
                    x.start_timestamp,
                    x.start_ms,
                    x.start_us,
                    x.thread_hash,
                    x.stacktrace_hash,
                    json_extract_string(x.fields, '$.thrownClass') AS thrown_class,
                    json_extract_string(x.fields, '$.message')     AS message
                FROM thrown x
            ),
            -- One row per throwable constructed. NOT EXISTS rather than NOT IN so that a row whose
            -- class did not extract survives to be dropped or named by the consumer, instead of
            -- turning the whole predicate NULL and taking every other row with it.
            distinct_throws AS (
                SELECT n.*
                FROM named n
                WHERE n.event_type = :error_throw_event_type
                   OR NOT EXISTS (
                        SELECT 1
                        FROM named twin
                        WHERE twin.event_type = :error_throw_event_type
                          AND twin.thrown_class = n.thrown_class)
            )""".replace("<<rehydrated>>", RawEventFields.REHYDRATED);

    private ThrowEvents() {
    }

    /**
     * The two parameters {@link #DISTINCT_THROWS_CTES} reads, ready for a statement to add its own.
     */
    static MapSqlParameterSource params() {
        return new MapSqlParameterSource()
                .addValue(EVENT_TYPES_PARAM, EVENT_TYPES)
                .addValue(ERROR_EVENT_TYPE_PARAM, ERROR_EVENT_TYPE);
    }
}
