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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.EventFieldRecord;
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.provider.profile.api.ThreadWindowEventRecord;
import cafe.jeffrey.provider.profile.api.ThreadWindowEventsPage;
import cafe.jeffrey.provider.profile.api.TraceContextCategory;
import cafe.jeffrey.provider.profile.api.TraceExceptionRecord;
import cafe.jeffrey.provider.profile.api.TraceListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.provider.profile.api.TraceOperationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationPage;
import cafe.jeffrey.provider.profile.api.TraceOperationRecord;
import cafe.jeffrey.provider.profile.api.TraceOperationSortField;
import cafe.jeffrey.provider.profile.api.TraceOperationSpanRecord;
import cafe.jeffrey.provider.profile.api.TraceOperationThreadsRecord;
import cafe.jeffrey.provider.profile.api.TraceNotificationRecord;
import cafe.jeffrey.provider.profile.api.TraceOverviewRecord;
import cafe.jeffrey.provider.profile.api.TracePage;
import cafe.jeffrey.provider.profile.api.TracePauseRecord;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.provider.profile.api.TraceSortField;
import cafe.jeffrey.provider.profile.api.TraceSpanContextRecord;
import cafe.jeffrey.provider.profile.api.TraceSpanRecord;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import cafe.jeffrey.provider.profile.api.TraceThrottleWindowRecord;
import cafe.jeffrey.provider.profile.api.TraceTimelineBucketRecord;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.common.model.SpanInterval;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static cafe.jeffrey.shared.persistence.GroupLabel.PROFILE_TRACES;

/**
 * Derives and reads the trace tables.
 * <p>
 * Ids are read out of the JSON {@code fields} with {@code json_extract_string(...)::BIGINT} rather
 * than {@code json_extract(...)::BIGINT}: the former hands back the number's raw text, so a 64-bit
 * id cannot lose precision on the way through.
 */
public class JdbcTraceRepository implements TraceRepository {

    /**
     * The event types that become spans, read out of the recording's own metadata rather than from a
     * list kept here: a type is a span if it declares a {@code spanId} field, which is exactly what
     * extending {@code AbstractTracedEvent} gives an event.
     * <p>
     * That is the whole of this class's knowledge about instrumented event types, and it names none
     * of them. An event type instrumented after this was written — including one declared outside
     * Jeffrey — takes part in traces with no change here, where a hard-coded list would have left it
     * silently missing from the Traces page until someone noticed. Naming works the same way: one
     * template per event type ({@link SpanNameTemplates}) — built-in for Jeffrey's own exchanges
     * on recordings that predate {@code @Span}, overlaid by what the recording declares.
     * <p>
     * The same set is what the drill-down excludes: an event that is itself a span belongs in the
     * waterfall, not in the list of what happened inside one.
     */
    //language=SQL
    /**
     * The event types that record a throw. Both feed trace_exceptions: the reader tells an Error
     * from an Exception by the event type the row kept, not by guessing from the class name.
     * <p>
     * Neither records a throw exactly once — an Error arrives as two {@code jdk.JavaExceptionThrow}
     * and one {@code jdk.JavaErrorThrow} — so the derivation collapses each throwable back to one
     * row before anything counts them. See {@code DERIVE_TRACE_EXCEPTIONS}.
     */
    private static final List<String> THROW_EVENT_TYPES =
            List.of(EventTypeName.JAVA_EXCEPTION_THROW, EventTypeName.JAVA_ERROR_THROW);

    /**
     * The same types as a SQL literal list, for the drill-down's exclusion predicate, whose other
     * exclusions are literals too.
     */
    private static final String THROW_EVENT_TYPES_SQL =
            THROW_EVENT_TYPES.stream().map(type -> "'" + type + "'").collect(Collectors.joining(", "));

    /**
     * Throws that mean the JVM or JDK failed to resolve or link something. Three closed hierarchies,
     * enumerated because SQL cannot ask about a class hierarchy: {@link LinkageError}, the
     * {@code java.lang.invoke} throwables, and {@link ReflectiveOperationException}.
     * <p>
     * The MethodHandle layer raises these by the handful while linking an {@code invokedynamic} call
     * site, and a library probing for an optional dependency does the same through
     * {@code Class.forName}. They land on the request thread inside the span that triggered the work,
     * so the attribution is right — they are just not something a reader of a trace can act on.
     * <p>
     * Only ever drops a throw the code <em>caught</em>: an escaped one survives the filter, because a
     * throw that failed its span is the story whoever threw it.
     * <p>
     * Taken as the closure of the three roots over every module in the JDK, so a new entry is only
     * needed if the JDK itself adds one. Deliberately absent: cancellation and timeouts, the resource
     * errors, and ordinary application logic — those are what a trace reader is looking for.
     */
    private static final List<String> RESOLUTION_FAILURE_CLASSES = List.of(
            // java.lang.LinkageError and every subclass
            "java.lang.LinkageError",
            "java.lang.BootstrapMethodError",
            "java.lang.ClassCircularityError",
            "java.lang.ClassFormatError",
            "java.lang.UnsupportedClassVersionError",
            "java.lang.ExceptionInInitializerError",
            "java.lang.IncompatibleClassChangeError",
            "java.lang.AbstractMethodError",
            "java.lang.IllegalAccessError",
            "java.lang.InstantiationError",
            "java.lang.NoSuchFieldError",
            "java.lang.NoSuchMethodError",
            "java.lang.NoClassDefFoundError",
            "java.lang.UnsatisfiedLinkError",
            "java.lang.VerifyError",
            "java.lang.reflect.GenericSignatureFormatError",
            // java.lang.invoke throwables
            "java.lang.invoke.WrongMethodTypeException",
            "java.lang.invoke.LambdaConversionException",
            "java.lang.invoke.StringConcatException",
            "java.lang.invoke.InvokerBytecodeGenerator$BytecodeGenerationException",
            // java.lang.ReflectiveOperationException and every subclass
            "java.lang.ReflectiveOperationException",
            "java.lang.ClassNotFoundException",
            "java.lang.IllegalAccessException",
            "java.lang.InstantiationException",
            "java.lang.NoSuchFieldException",
            "java.lang.NoSuchMethodException",
            "java.lang.reflect.InvocationTargetException",
            "java.lang.reflect.Proxy$InvocationException");

    private static final String SPAN_EVENT_TYPES = """
            SELECT name FROM event_types
            WHERE list_contains(json_extract_string(columns, '$[*].field'), 'spanId')""";

    /**
     * Existence probe over {@link #SPAN_EVENT_TYPES}, formatted from it rather than written out
     * again so the two can never disagree about what makes an event type a span.
     */
    //language=SQL
    private static final String SPAN_EVENT_TYPES_EXIST =
            "SELECT COUNT(*) FROM (%s LIMIT 1) probe".formatted(SPAN_EVENT_TYPES);


    /**
     * Crossing from the microseconds a span is measured in into the milliseconds the events table is
     * keyed on. Flooring rather than rounding is what puts the bound on the millisecond a sample
     * taken at that instant was filed under.
     */
    private static final long MICROS_PER_MILLI = 1_000L;

    /**
     * The keys every traced event carries as plumbing rather than as detail: the columns JFR fills in
     * and the span already has fields of its own for, plus the span shape the derivation has consumed
     * by the time it builds this. What is left is the event's own declared fields — a statement's SQL
     * and row count, an exchange's URI and status code.
     * <p>
     * Merging a patch of nulls is how DuckDB deletes keys, which keeps this one expression for every
     * event type. Projecting the keys per type instead would need editing each time an instrumented
     * event gains a field, and would silently drop the new one until someone did.
     * <p>
     * A hand-written span declares nothing beyond the span shape, so it strips down to an empty
     * object — which is why the projection nulls that out rather than storing an empty object.
     */
    private static final String PLUMBING_KEYS = """
            "startTime":null,"duration":null,"eventThread":null,\
            "traceId":null,"spanId":null,"parentSpanId":null,\
            "name":null,"kind":null,"errorType":null,"attributes":null""";

    /**
     * The whole {@code event_fields} projection: the plumbing patch applied, the result nulled out
     * when nothing of the event's own remains.
     * <p>
     * {@code status} joins the patch per row rather than once, because it is not always plumbing:
     * where an exchange recorded no {@code statusCode}, that key holds its response code, which is
     * detail about the operation and the only record of it. Stripping it unconditionally is what
     * took the 500 out of the span detail of every recording that spells the outcome that way.
     */
    private static final String EVENT_FIELDS_PROJECTION = """
            NULLIF(CAST(json_merge_patch(fields,
                CASE WHEN %s THEN '{%s,"status":null}' ELSE '{%s}' END) AS VARCHAR), '{}')"""
            .formatted(SpanConventions.recordedStatusIsSpanStatus(), PLUMBING_KEYS, PLUMBING_KEYS);

    /**
     * The {@code events} view's own splice, applied to a raw row aliased {@code e} joined to
     * {@code field_texts t}.
     * <p>
     * The promotion below reads {@code events_raw} rather than the view — it mints span ids from
     * {@code rowid}, which only a physical table has — but a promoted span's payload has to be the
     * JSON the recording declared, pooled field included. Rehydrating here with the view's own
     * expression is what keeps the payload this derivation hashes identical to the one a reader
     * sees; without it, a blocking event whose largest field was pooled hashed to a payload row
     * that was never written, and the span pointed at nothing.
     */
    private static final String REHYDRATED_FIELDS = """
            CASE WHEN e.pooled_text_hash IS NULL
                 THEN e.fields
                 ELSE json_merge_patch(e.fields, json_object(e.pooled_field, t.text)) END""";

    /*
     * The distinct event_fields payloads, written before the spans so every reference the span
     * insert computes has a row to land on. One row per distinct JSON text, keyed by the text's own
     * hash: a million statement spans carry a few thousand distinct payloads, and storing the text
     * once per payload instead of once per span is most of the difference between a trace table
     * that fits in memory and one that dominates the profile file.
     *
     * Two branches, mirroring the two the span insert below assembles. Each reads its events
     * exactly as that insert does -- the recorded branch through the view, the blocking branch off
     * the raw table with the view's splice reapplied -- because the payload text hashed here and
     * the payload text hashed there have to be the same string.
     *
     * The blocking branch admits every blocking event's payload, promoted into a span or not --
     * deciding promotion needs the recorded spans in hand, and a few unreferenced payload rows are
     * cheaper than running the promotion twice.
     */
    //language=SQL
    private static final String DERIVE_SPAN_PAYLOADS = """
            INSERT INTO trace_span_payloads (payload_id, payload)
            SELECT DISTINCT CAST(mod(hash(payload), CAST(9223372036854775807 AS UBIGINT)) AS BIGINT), payload
            FROM (
                SELECT %s AS payload
                FROM events
                WHERE event_type IN (%s)
                  AND COALESCE(json_extract_string(fields, '$.traceId')::BIGINT, 0) <> 0
                  AND COALESCE(json_extract_string(fields, '$.spanId')::BIGINT, 0) <> 0
                UNION ALL
                SELECT NULLIF(CAST(json_merge_patch(<<rehydrated>>, '{%s}') AS VARCHAR), '{}') AS payload
                FROM events_raw e
                LEFT JOIN field_texts t ON t.text_hash = e.pooled_text_hash
                WHERE e.event_type IN (:promoted_event_types)
                  AND e.thread_hash IS NOT NULL
            )
            WHERE payload IS NOT NULL
            """.replace("<<rehydrated>>", REHYDRATED_FIELDS);

    /*
     * The identity columns are a flat projection, because there is nothing left to work out: Tracer
     * minted every id in the JVM, so each is read straight out of the JSON. What used to be a
     * synthetic span id here now lives in the event classes.
     *
     * The three shape columns are not flat, and deliberately so: each is a projection over
     * conventions, which settle what an exchange, a call or a statement is *called* rather than
     * reading back whichever answer the recording's instrumentation cached in a field. That is what
     * makes one endpoint one operation across recordings, and what lets instrumentation that stamps
     * the trace ids and its own fields land under a name rather than under its event type.
     *
     * Naming is one concept, the template: SpanNameTemplates renders a template per event type --
     * built-in for Jeffrey's own exchanges on recordings that predate @Span, overlaid by what
     * the recording declares for itself, which is how an event type Jeffrey has never seen gets
     * named with no change here. The verdict is never declared -- it is recorded by the writer,
     * and the built-in status arms exist only for the exchange types Jeffrey itself knows.
     * Discovery above stays structural, so an event type no convention covers is still a span; it
     * just carries the name, kind and status it recorded for itself.
     *
     * The ids are pulled out once in the CTE, which the filter then reuses by name, so each is read
     * out of the JSON a single time. The two predicates drop events that were never part of a
     * trace: their id fields are 0, the wire encoding for "absent".
     *
     * QUALIFY enforces the invariant the primary key states: one row per (trace_id, span_id). A
     * duplicated event -- a re-imported chunk, third-party instrumentation reusing an id -- keeps
     * its earliest occurrence, which is the row the waterfall would have drawn anyway, and because
     * the dedupe happens before DERIVE_TRACES runs, span_count and error_count agree with the
     * waterfall by construction instead of over-counting rows nobody renders.
     *
     * One statement rather than the two inserts and the self-duration UPDATE it used to be. The
     * UPDATE rewrote nearly every row group of the table it had just filled -- the freed versions
     * stayed dead space in the profile file -- and a second insert after the first foreclosed
     * ordering the table as a whole. As CTEs over the same intermediates, the promotion still sees
     * the recorded spans it attributes against, the self-time merge still sees every span, and the
     * single INSERT writes each row once, ordered by (trace_id, start_timestamp) so a one-trace
     * read prunes to a handful of row groups by zone map alone -- this table carries no secondary
     * index to do it.
     *
     * The stages, in reading order:
     *   spans/recorded  -- the recorded span events, deduped, shape projected (as before)
     *   mapping..minted -- the JDK blocking events promoted to leaf spans (as before, joining
     *                      `recorded` where the old statement joined the half-filled table)
     *   adoptions       -- recorded spans re-hung under the method span that wraps them
     *   all_spans       -- both, in one relation, recorded parents rewritten where adopted
     *   clipped..covered -- the same-thread-children interval merge (the old SELF_DURATIONS pass)
     *   final SELECT    -- self time subtracted where children covered it, payload hashed into
     *                      its reference (the text itself lives in trace_span_payloads)
     *
     * The promotion stages (mapping through promoted) synthesize one leaf span out of every JDK
     * blocking event that began inside a span on its own thread -- what turns "this span carried
     * 40ms of Socket I/O" from a summed count into a bar with a position, a duration and a payload.
     * The promoted set, names and kinds live in BlockingLeafSpans; the mapping arrives as
     * positional UNNEST lists so that class stays the only place it is written down.
     *
     * The parent is the *innermost* span whose window contains the event's start on the same
     * thread: latest start wins, smallest window breaks the tie, span id makes it deterministic.
     * An event outside every span is not promoted at all -- there is no trace to hang it on.
     *
     * Adoption is the counterpart for recorded spans. A recorded span's parentSpanId names the span
     * the Tracer's context held when it was created, and jdk.MethodTrace is invisible to that
     * context -- so a traced method wrapping recorded work (a traced controller method around an
     * entire request) would land as a SIBLING of the spans it contains, and its self time would
     * claim the whole request. The adoptions stage re-hangs such a recorded span under the
     * innermost method span standing between it and its own recorded parent: the method's anchor
     * (the innermost recorded span open at the method's start -- the same span that decided its
     * trace) must be exactly the child's recorded parent, and the threads must match. That is what
     * keeps instrumentation authoritative: a method span only ever slots in between a recorded
     * parent and its children, never moves a span across recorded ancestry or onto another thread.
     *
     * Its span id is minted, which the derivation otherwise deliberately no longer does: a jdk.*
     * event can never carry a recorded id, so the choice is a minted id or no bar. The id is a hash
     * of the event's identity folded into [1, 2^63-1] -- never 0, the wire encoding for "absent",
     * and never negative, so a minted id is recognisable in a pinch. rowid is in the hash so two
     * byte-identical events (a re-imported chunk) mint two ids instead of colliding; the NOT EXISTS
     * guards the astronomically unlikely collision with a recorded id, because failing the primary
     * key here would fail the whole profile initialization.
     *
     * COALESCE(duration, 0): JFR writes some of these events with a null duration, and a wait of
     * unknown length still deserves its row -- it draws at the bar floor rather than asserting a
     * length the recording never claimed.
     */
    //language=SQL
    private static final String DERIVE_TRACE_SPANS = """
            INSERT INTO trace_spans (
                trace_id, span_id, parent_span_id, name, kind, status, error_type,
                start_timestamp, start_timestamp_from_beginning, duration, self_duration,
                thread_hash, event_type, attributes, event_fields_ref, synthesized, io_origin)
            WITH spans AS (
                SELECT
                    e.*,
                    json_extract_string(e.fields, '$.traceId')::BIGINT                  AS trace_id,
                    json_extract_string(e.fields, '$.spanId')::BIGINT                   AS span_id,
                    NULLIF(json_extract_string(e.fields, '$.parentSpanId')::BIGINT, 0)  AS parent_span_id
                FROM events e
                WHERE e.event_type IN (%s)
                  AND COALESCE(trace_id, 0) <> 0
                  AND COALESCE(span_id, 0) <> 0
            ),
            recorded AS (
                SELECT
                    trace_id                                                        AS trace_id,
                    span_id                                                         AS span_id,
                    parent_span_id                                                  AS parent_span_id,
                    %s                                                              AS name,
                    %s                                                              AS kind,
                    %s                                                              AS status,
                    json_extract_string(fields, '$.errorType')                      AS error_type,
                    start_timestamp                                                 AS start_timestamp,
                    COALESCE(start_timestamp_from_beginning, 0)                     AS start_ms,
                    COALESCE(duration, 0)                                           AS duration,
                    thread_hash                                                     AS thread_hash,
                    event_type                                                      AS event_type,
                    json_extract_string(fields, '$.attributes')                     AS attributes,
                    %s                                                              AS payload,
                    FALSE                                                           AS synthesized
                FROM spans
                QUALIFY ROW_NUMBER() OVER (PARTITION BY trace_id, span_id
                                           ORDER BY start_timestamp, duration) = 1
            ),
            mapping AS (
                SELECT UNNEST([:blocking_event_types]) AS event_type,
                       UNNEST([:blocking_names])       AS name,
                       UNNEST([:blocking_kinds])       AS kind
            ),
            /*
             * Which stack traces belong to a class loader, as a set of hashes to probe.
             *
             * Scoped by the join to events_raw: only stacks a promoted event actually references are
             * unnested, so this never walks the profile's whole stacktraces table, which on a
             * sampled recording is orders of magnitude larger than the set of blocking events.
             *
             * Every frame is examined, not just the leaf-most few. A stack whose only loader frame
             * sits near the root -- I/O reached from inside a static initializer the loader is
             * running -- is class-loading work by any reading a reader would give it, and this
             * derivation runs once per profile, so there is nothing to buy by stopping early.
             */
            class_loading_stacks AS (
                SELECT DISTINCT st.stacktrace_hash AS stacktrace_hash
                FROM stacktraces st
                JOIN events_raw io
                  ON io.stacktrace_hash = st.stacktrace_hash
                 AND io.event_type IN (:class_loading_event_types)
                CROSS JOIN UNNEST(st.frame_hashes) AS u(frame_hash)
                JOIN frames f ON f.frame_hash = u.frame_hash
                WHERE f.class_name IS NOT NULL
                  AND EXISTS (
                      SELECT 1
                      FROM UNNEST([:class_loading_frame_prefixes]) p(prefix)
                      WHERE starts_with(f.class_name, p.prefix))
            ),
            -- events_raw rather than the events view, because rowid -- which the minted span id
            -- below folds in -- exists only on a physical table. The pooled field is spliced back
            -- by hand here (see REHYDRATED_FIELDS): a promoted wait's payload is the recording's
            -- own JSON, and it has to hash to the row DERIVE_SPAN_PAYLOADS wrote.
            blocking AS (
                SELECT
                    e.rowid                                         AS event_row,
                    e.event_type                                    AS event_type,
                    e.start_timestamp                               AS start_timestamp,
                    COALESCE(e.start_timestamp_from_beginning, 0)   AS start_ms,
                    COALESCE(e.duration, 0)                         AS duration,
                    e.thread_hash                                   AS thread_hash,
                    <<rehydrated>>                                  AS fields,
                    m.name                                          AS name,
                    m.kind                                          AS kind,
                    EPOCH_US(e.start_timestamp)                     AS start_us,
                    -- NULL rather than a negative verdict: a recording with no stack traces lands
                    -- here too, and it has not established that this read was anything.
                    CASE WHEN cls.stacktrace_hash IS NOT NULL
                         THEN :class_loading_origin END             AS io_origin
                FROM events_raw e
                LEFT JOIN field_texts t ON t.text_hash = e.pooled_text_hash
                JOIN mapping m ON m.event_type = e.event_type
                LEFT JOIN class_loading_stacks cls ON cls.stacktrace_hash = e.stacktrace_hash
                WHERE e.event_type IN (:blocking_event_types)
                  AND e.thread_hash IS NOT NULL
            ),
            -- jdk.MethodTrace (JEP 520), rehydrated. Split from the naming below rather than
            -- projected alongside it: an unqualified `fields` in a SELECT over events_raw resolves
            -- to that table's own column, not to the alias beside it, and the traced method's
            -- qualified name is long enough to have been pooled out of exactly that column -- so
            -- naming here read no method at all and every span fell back to "Traced method".
            method_events AS (
                SELECT
                    e.rowid                                         AS event_row,
                    e.event_type                                    AS event_type,
                    e.start_timestamp                               AS start_timestamp,
                    COALESCE(e.start_timestamp_from_beginning, 0)   AS start_ms,
                    COALESCE(e.duration, 0)                         AS duration,
                    e.thread_hash                                   AS thread_hash,
                    <<rehydrated>>                                  AS fields
                FROM events_raw e
                LEFT JOIN field_texts t ON t.text_hash = e.pooled_text_hash
                WHERE e.event_type = :method_event_type
                  AND e.thread_hash IS NOT NULL
            ),
            -- Unlike a blocking event a traced method names itself -- the name is read out of the
            -- event rather than taken from a fixed label -- and it NESTS: a traced method's duration
            -- includes the methods it calls, so one can contain another and a blocking wait can
            -- happen inside one. `nests` is what carries that difference forward.
            methods AS (
                SELECT
                    event_row, event_type, start_timestamp, start_ms, duration, thread_hash, fields,
                    %s
                    EPOCH_US(start_timestamp)                       AS start_us,
                    TRUE                                            AS nests
                FROM method_events
            ),
            candidates AS (
                SELECT event_row, event_type, start_timestamp, start_ms, duration, thread_hash,
                       fields, name, kind, start_us, FALSE AS nests, io_origin
                FROM blocking
                UNION ALL
                -- A traced method is not an I/O operation, so it has no origin to carry.
                SELECT event_row, event_type, start_timestamp, start_ms, duration, thread_hash,
                       fields, name, kind, start_us, nests, NULL AS io_origin
                FROM methods
            ),
            -- Which trace a candidate belongs to, answered by the innermost RECORDED span open on
            -- its thread. Trace identity comes from instrumentation and only from instrumentation:
            -- a method span is inside a trace because a recorded span contains it, never because
            -- another method span does. That innermost recorded span is kept as the candidate's
            -- ANCHOR: for a method span it is the recorded span it conceptually runs inside, which
            -- is what the adoptions stage below checks before letting the method span take over
            -- that recorded span's children.
            traced AS (
                SELECT
                    c.*,
                    s.trace_id  AS trace_id,
                    s.span_id   AS anchor_span_id
                FROM candidates c
                JOIN recorded s
                  ON s.thread_hash = c.thread_hash
                 AND c.start_us >= EPOCH_US(s.start_timestamp)
                 AND c.start_us <= EPOCH_US(s.start_timestamp) + s.duration // 1000
                QUALIFY ROW_NUMBER() OVER (
                    PARTITION BY c.event_row
                    ORDER BY EPOCH_US(s.start_timestamp) DESC,
                             EPOCH_US(s.start_timestamp) + s.duration // 1000 ASC,
                             s.span_id) = 1
            ),
            -- Minted before parenting, which is what makes nesting possible at all: the id is a pure
            -- function of columns already in hand, so a method span can be offered as a parent in the
            -- same statement that creates it, with no recursion and no second insert.
            minted AS (
                SELECT
                    t.*,
                    1 + CAST(mod(
                            hash(CONCAT_WS('|', t.trace_id, t.thread_hash, t.event_type,
                                                t.start_us, t.event_row)),
                            CAST(9223372036854775806 AS UBIGINT)) AS BIGINT) AS span_id
                FROM traced t
            ),
            -- What a promoted span may hang under: every recorded span, plus the method spans just
            -- minted. Blocking candidates are deliberately absent -- nothing nests inside a wait.
            parent_candidates AS (
                SELECT
                    trace_id, span_id, thread_hash,
                    EPOCH_US(start_timestamp)                       AS from_us,
                    EPOCH_US(start_timestamp) + duration // 1000    AS to_us
                FROM recorded
                UNION ALL
                SELECT
                    trace_id, span_id, thread_hash,
                    start_us                                        AS from_us,
                    start_us + duration // 1000                     AS to_us
                FROM minted
                WHERE nests
            ),
            /*
             * The parent: the innermost candidate whose window contains the child's start.
             *
             * The three-part guard is what keeps this a tree. Two method spans that begin on the
             * same microsecond each contain the other's start, so containment alone would let them
             * adopt each other and the tree would close into a cycle. The guard admits a parent only
             * when it strictly precedes the child in the total order (from ASC, to DESC, span_id
             * ASC) -- an order in which a container always precedes what it contains -- so a cycle
             * cannot be expressed. The QUALIFY then reads that same order backwards to pick the
             * innermost of the candidates that survived.
             */
            parented AS (
                SELECT
                    m.*,
                    p.span_id   AS parent_span_id
                FROM minted m
                JOIN parent_candidates p
                  ON p.trace_id = m.trace_id
                 AND p.thread_hash = m.thread_hash
                 AND m.start_us >= p.from_us
                 AND m.start_us <= p.to_us
                 AND (p.from_us < m.start_us
                      OR (p.from_us = m.start_us
                          AND (p.to_us > m.start_us + m.duration // 1000
                               OR (p.to_us = m.start_us + m.duration // 1000
                                   AND p.span_id < m.span_id))))
                QUALIFY ROW_NUMBER() OVER (
                    PARTITION BY m.event_row
                    ORDER BY p.from_us DESC, p.to_us ASC, p.span_id) = 1
            ),
            promoted AS (
                SELECT
                    m.trace_id                                          AS trace_id,
                    m.span_id                                           AS span_id,
                    m.parent_span_id                                    AS parent_span_id,
                    m.name                                              AS name,
                    m.kind                                              AS kind,
                    'UNSET'                                             AS status,
                    NULL                                                AS error_type,
                    m.start_timestamp                                   AS start_timestamp,
                    m.start_ms                                          AS start_ms,
                    m.duration                                          AS duration,
                    m.thread_hash                                       AS thread_hash,
                    m.event_type                                        AS event_type,
                    NULL                                                AS attributes,
                    NULLIF(CAST(json_merge_patch(m.fields, '{%s}') AS VARCHAR), '{}') AS payload,
                    TRUE                                                AS synthesized,
                    m.io_origin                                         AS io_origin
                FROM parented m
                WHERE NOT EXISTS (
                    SELECT 1 FROM recorded t
                    WHERE t.trace_id = m.trace_id AND t.span_id = m.span_id)
                QUALIFY ROW_NUMBER() OVER (PARTITION BY m.trace_id, m.span_id
                                           ORDER BY m.start_timestamp) = 1
            ),
            /*
             * A recorded span whose start falls inside a method span standing between it and its
             * own recorded parent is re-hung under that method span -- the innermost one, by the
             * same order the promotion parenting reads. `anchor_span_id = r.parent_span_id` is the
             * whole rule: the method span must itself hang (directly or through other method
             * spans) under exactly the recorded span the child names as its parent, so adoption
             * only ever interposes a method chain between a recorded parent and its children --
             * never across recorded ancestry, and never across threads.
             *
             * The strict `<` on the start is not a tie-break but a statement of impossibility: a
             * method span starting in the same microsecond as the recorded span is CONTAINED by
             * that span as far as the anchor search can see, so its anchor is that span or
             * something inside it -- never that span's parent -- and the join's anchor equality
             * already fails. Spelling it strictly also keeps the whole graph provably acyclic:
             * every adoption edge goes from a strictly earlier start to a later one, and no other
             * edge kind decreases the start, so no cycle can contain an adoption.
             */
            adoptions AS (
                SELECT
                    r.trace_id                                          AS trace_id,
                    r.span_id                                           AS span_id,
                    m.span_id                                           AS method_parent_span_id
                FROM recorded r
                JOIN minted m
                  ON m.nests
                 AND m.trace_id = r.trace_id
                 AND m.anchor_span_id = r.parent_span_id
                 AND m.thread_hash = r.thread_hash
                 AND m.start_us < EPOCH_US(r.start_timestamp)
                 AND EPOCH_US(r.start_timestamp) <= m.start_us + m.duration // 1000
                QUALIFY ROW_NUMBER() OVER (
                    PARTITION BY r.trace_id, r.span_id
                    ORDER BY m.start_us DESC,
                             m.start_us + m.duration // 1000 ASC,
                             m.span_id) = 1
            ),
            all_spans AS (
                SELECT r.trace_id                                       AS trace_id,
                       r.span_id                                        AS span_id,
                       COALESCE(a.method_parent_span_id, r.parent_span_id) AS parent_span_id,
                       r.name, r.kind, r.status, r.error_type,
                       r.start_timestamp, r.start_ms, r.duration, r.thread_hash, r.event_type,
                       r.attributes, r.payload, r.synthesized,
                       -- A recorded span is instrumentation, not an I/O operation the derivation
                       -- inferred a reason for.
                       NULL                                             AS io_origin
                FROM recorded r
                LEFT JOIN adoptions a
                  ON a.trace_id = r.trace_id
                 AND a.span_id = r.span_id
                UNION ALL
                SELECT trace_id, span_id, parent_span_id, name, kind, status, error_type,
                       start_timestamp, start_ms, duration, thread_hash, event_type,
                       attributes, payload, synthesized, io_origin
                FROM promoted
            ),
            clipped AS (
                SELECT
                    c.trace_id                                          AS trace_id,
                    c.parent_span_id                                    AS span_id,
                    GREATEST(EPOCH_US(c.start_timestamp),
                             EPOCH_US(p.start_timestamp))               AS from_us,
                    LEAST(EPOCH_US(c.start_timestamp) + c.duration // 1000,
                          EPOCH_US(p.start_timestamp) + p.duration // 1000) AS to_us
                FROM all_spans c
                JOIN all_spans p
                  ON p.trace_id = c.trace_id
                 AND p.span_id = c.parent_span_id
                WHERE c.parent_span_id IS NOT NULL
                  AND COALESCE(c.thread_hash, 0) = COALESCE(p.thread_hash, 0)
            ),
            windows AS (
                SELECT * FROM clipped WHERE to_us > from_us
            ),
            numbered AS (
                SELECT
                    trace_id, span_id, from_us, to_us,
                    CASE WHEN from_us > MAX(to_us) OVER (
                             PARTITION BY trace_id, span_id
                             ORDER BY from_us, to_us
                             ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING)
                         THEN 1 ELSE 0 END AS starts_island
                FROM windows
            ),
            islands AS (
                SELECT
                    trace_id, span_id, from_us, to_us,
                    SUM(starts_island) OVER (
                        PARTITION BY trace_id, span_id
                        ORDER BY from_us, to_us
                        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS island
                FROM numbered
            ),
            merged AS (
                SELECT
                    trace_id, span_id,
                    MIN(from_us) AS from_us,
                    MAX(to_us)   AS to_us
                FROM islands
                GROUP BY trace_id, span_id, island
            ),
            covered AS (
                SELECT trace_id, span_id, SUM(to_us - from_us) AS covered_us
                FROM merged
                GROUP BY trace_id, span_id
            )
            SELECT
                s.trace_id                                              AS trace_id,
                s.span_id                                               AS span_id,
                s.parent_span_id                                        AS parent_span_id,
                s.name                                                  AS name,
                s.kind                                                  AS kind,
                s.status                                                AS status,
                s.error_type                                            AS error_type,
                s.start_timestamp                                       AS start_timestamp,
                s.start_ms                                              AS start_timestamp_from_beginning,
                s.duration                                              AS duration,
                -- The span's own time: children on another thread run beside it, not instead of it,
                -- so only the merged same-thread cover is subtracted; floored at zero because
                -- microsecond rounding can make merged children marginally outlast their parent.
                CASE WHEN c.covered_us IS NULL THEN s.duration
                     ELSE GREATEST(0, s.duration - c.covered_us * 1000) END AS self_duration,
                s.thread_hash                                           AS thread_hash,
                s.event_type                                            AS event_type,
                s.attributes                                            AS attributes,
                CASE WHEN s.payload IS NULL THEN NULL
                     ELSE CAST(mod(hash(s.payload),
                                   CAST(9223372036854775807 AS UBIGINT)) AS BIGINT)
                END                                                     AS event_fields_ref,
                s.synthesized                                           AS synthesized,
                s.io_origin                                             AS io_origin
            FROM all_spans s
            LEFT JOIN covered c
              ON c.trace_id = s.trace_id
             AND c.span_id = s.span_id
            ORDER BY s.trace_id, s.start_timestamp, s.span_id
            """.replace("<<rehydrated>>", REHYDRATED_FIELDS);

    /**
     * The keys a JDK blocking event carries as plumbing rather than as detail. Narrower than
     * {@link #PLUMBING_KEYS}: a jdk.* event never declares the span shape, so only the JFR-filled
     * columns are stripped and everything else — host, port, path, monitorClass, parkedClass — is
     * the operation's own payload and survives into the span's {@code trace_span_payloads} row.
     */
    private static final String JDK_EVENT_PLUMBING_KEYS =
            "\"startTime\":null,\"duration\":null,\"eventThread\":null";

    /*
     * The root is the earliest span without a parent. The ordering falls back to the earliest span
     * of any kind, so a trace whose real root went unrecorded -- below the event threshold, or
     * simply not instrumented -- still gets a name instead of dropping out of the list. `span_id`
     * breaks the remaining tie: two parentless spans starting in the same microsecond would
     * otherwise pick an arbitrary root, and the root's name is the trace's *operation*, so an
     * arbitrary choice there is an arbitrary answer to "what kind of request was this".
     *
     * Duration spans the whole trace rather than reusing the root's own duration: the same number
     * whenever the root encloses its children, and the more honest one when it does not. The value
     * is nanosecond-valued but only microsecond-accurate at its endpoints: `s.duration` carries
     * full nanoseconds, while `epoch_ns(start_timestamp)` scales up a microsecond-resolution
     * TIMESTAMPTZ, whose sub-microsecond part was already gone.
     *
     * `has_platform_span` is settled here, once, rather than by a correlated EXISTS per query.
     * `th.is_virtual = FALSE` is TRUE only for a thread that resolved *and* is a platform thread;
     * an unresolved thread yields NULL, which BOOL_OR skips, so a trace nobody could place ends up
     * FALSE -- it cannot promise samples, and offering a flamegraph for it produces an empty one.
     */
    //language=SQL
    private static final String DERIVE_TRACES = """
            INSERT INTO traces (
                trace_id, root_name, root_kind, root_event_type, root_span_id, start_timestamp,
                start_timestamp_from_beginning, duration, span_count, error_count, has_platform_span)
            WITH roots AS (
                SELECT trace_id, name, kind, event_type, span_id,
                       ROW_NUMBER() OVER (PARTITION BY trace_id
                                          ORDER BY (parent_span_id IS NOT NULL),
                                                   start_timestamp,
                                                   span_id) AS rn
                FROM trace_spans
            ),
            aggregated AS (
                SELECT s.trace_id                                                          AS trace_id,
                       MIN(s.start_timestamp)                                              AS start_timestamp,
                       MIN(s.start_timestamp_from_beginning)                               AS start_ms,
                       MAX(epoch_ns(s.start_timestamp) + s.duration)
                           - MIN(epoch_ns(s.start_timestamp))                              AS duration,
                       COUNT(*)                                                            AS span_count,
                       COUNT(*) FILTER (WHERE s.status = 'ERROR')                          AS error_count,
                       COALESCE(BOOL_OR(th.is_virtual = FALSE), FALSE)                     AS has_platform_span
                FROM trace_spans s
                LEFT JOIN threads th ON th.thread_hash = s.thread_hash
                GROUP BY s.trace_id
            )
            SELECT a.trace_id, r.name, r.kind, r.event_type, r.span_id, a.start_timestamp, a.start_ms,
                   a.duration, a.span_count::INTEGER, a.error_count::INTEGER, a.has_platform_span
            FROM aggregated a
            JOIN roots r ON r.trace_id = a.trace_id AND r.rn = 1
            """;

    //language=SQL
    private static final String DELETE_TRACE_SPANS = "DELETE FROM trace_spans";

    //language=SQL
    private static final String DELETE_SPAN_PAYLOADS = "DELETE FROM trace_span_payloads";

    //language=SQL
    private static final String DELETE_TRACES = "DELETE FROM traces";

    /*
     * Both reads are ordered by their own instant, which is what lets the rails draw them without
     * sorting, and the detail regions list them in the order they happened.
     */
    //language=SQL
    private static final String NOTIFICATIONS_OF_TRACE = """
            SELECT trace_id, span_id, notification_id,
                   start_timestamp_from_beginning AS start_ms,
                   EPOCH_US(start_timestamp)      AS start_epoch_us,
                   type, m.message_text            AS message,
                   severity, category, source, attributes,
                   COALESCE(thread_hash, 0)       AS thread_hash
            FROM trace_notifications n
            LEFT JOIN trace_notification_messages m ON m.message_id = n.message_ref
            WHERE trace_id = :trace_id
            ORDER BY start_timestamp, notification_id
            """;

    //language=SQL
    private static final String EXCEPTIONS_OF_TRACE = """
            SELECT trace_id, span_id, exception_id,
                   start_timestamp_from_beginning AS start_ms,
                   EPOCH_US(start_timestamp)      AS start_epoch_us,
                   event_type, thrown_class, message, escaped, stacktrace_hash,
                   COALESCE(thread_hash, 0)       AS thread_hash
            FROM trace_exceptions
            WHERE trace_id = :trace_id
            ORDER BY start_timestamp, exception_id
            """;

    /*
     * One stack, resolved to frames, topmost first.
     *
     * The array is stored root-first -- JfrEventReader writes getFrames().reversed() -- so the
     * ordinal has to come along and the result is ordered by it descending. Unnesting alone would
     * lose the order entirely: a bag of frame hashes says who is on the stack and nothing about who
     * called whom, and the two frames a reader actually wants are the ones at the ends.
     *
     * generate_subscripts pairs each element with its 1-based position in the same projection, so
     * the ordinal survives the unnest without a second scan of the array.
     */
    //language=SQL
    private static final String STACKTRACE_FRAMES = """
            WITH positioned AS (
                SELECT unnest(frame_hashes)                  AS frame_hash,
                       generate_subscripts(frame_hashes, 1)  AS depth
                FROM stacktraces
                WHERE stacktrace_hash = :stacktrace_hash
            )
            SELECT f.class_name, f.method_name, f.frame_type, f.bytecode_index, f.line_number, f.hidden_class_id
            FROM positioned p
            JOIN frames f ON f.frame_hash = p.frame_hash
            ORDER BY p.depth DESC
            """;

    //language=SQL
    private static final String DELETE_TRACE_NOTIFICATIONS = "DELETE FROM trace_notifications";

    //language=SQL
    private static final String DELETE_TRACE_EXCEPTIONS = "DELETE FROM trace_exceptions";

    /*
     * What the application said while a trace ran.
     *
     * A notification stamps the enclosing span's ids onto itself at commit time, so this is a copy,
     * not an inference -- which is the whole reason the fields exist. Thread plus window would be
     * wrong here in a way it is not for a throw: a notification can be raised on a pool thread or a
     * callback for work that belongs to another span entirely.
     *
     * events_raw rather than the events view, and the pooled field spliced back by hand, because a
     * notification's `message` is exactly the kind of long string the parser pools -- and because
     * rowid, which becomes the notification's id, exists only on a physical table.
     *
     * The LEFT JOIN nulls a span id that names a span this profile does not hold: below a JFR
     * threshold, or in a chunk that was never ingested. A dangling id would make `span_id IS NOT
     * NULL` stop meaning "there is a bar to draw this on", which is the one thing the reader asks it.
     *
     * The event type is bound rather than discovered, which is the one place this derivation is not
     * structural the way SPAN_EVENT_TYPES is. That is deliberate. Discovery pays off when the reader
     * can do something with a type it has never seen, and for a span it can -- a discovered span gets
     * a bar, a name, a kind and a duration from columns AbstractTracedEvent guarantees. For an
     * instant it cannot: the rail mark's colour, the popover's title and the severity ranking all
     * come from the notification-shaped columns below, which a generic instant does not have. A
     * discovered one would draw an unlabelled mark over a row of NULLs.
     *
     * When a second instant family does arrive, the shape is a generic trace_instants table filled by
     * discovery on the declared `enclosingSpanId` column -- the marker AbstractTracedInstant exists to
     * make a contract rather than a spelling -- with this table as its notification-shaped projection.
     */
    /*
     * The distinct message texts, before the notifications that reference them.
     *
     * Same shape as DERIVE_SPAN_PAYLOADS: hash the text, insert each distinct one once, and let the
     * notification insert below compute the same hash inline rather than joining back to find it.
     */
    //language=SQL
    private static final String DERIVE_TRACE_NOTIFICATION_MESSAGES = """
            INSERT INTO trace_notification_messages (message_id, message_text)
            SELECT DISTINCT CAST(mod(hash(message), CAST(9223372036854775807 AS UBIGINT)) AS BIGINT), message
            FROM (
                SELECT json_extract_string(<<rehydrated>>, '$.message') AS message
                FROM events_raw e
                LEFT JOIN field_texts t ON t.text_hash = e.pooled_text_hash
                WHERE e.event_type = :notification_event_type
            )
            WHERE message IS NOT NULL
            """.replace("<<rehydrated>>", REHYDRATED_FIELDS);

    //language=SQL
    private static final String DELETE_TRACE_NOTIFICATION_MESSAGES =
            "DELETE FROM trace_notification_messages";

    //language=SQL
    private static final String DERIVE_TRACE_NOTIFICATIONS = """
            INSERT INTO trace_notifications (trace_id, span_id, notification_id, start_timestamp,
                                             start_timestamp_from_beginning, type, message_ref,
                                             severity, category, source, attributes, thread_hash)
            WITH raised AS (
                SELECT
                    e.rowid                                       AS notification_id,
                    e.start_timestamp                             AS start_timestamp,
                    COALESCE(e.start_timestamp_from_beginning, 0)  AS start_ms,
                    e.thread_hash                                 AS thread_hash,
                    <<rehydrated>>                                AS fields
                FROM events_raw e
                LEFT JOIN field_texts t ON t.text_hash = e.pooled_text_hash
                WHERE e.event_type = :notification_event_type
            ),
            identified AS (
                SELECT
                    CAST(json_extract_string(fields, '$.traceId') AS BIGINT)         AS trace_id,
                    NULLIF(CAST(json_extract_string(fields, '$.enclosingSpanId') AS BIGINT), 0) AS span_id,
                    notification_id,
                    start_timestamp,
                    start_ms,
                    json_extract_string(fields, '$.type')                            AS type,
                    CAST(mod(hash(json_extract_string(fields, '$.message')),
                             CAST(9223372036854775807 AS UBIGINT)) AS BIGINT)        AS message_ref,
                    json_extract_string(fields, '$.severity')                        AS severity,
                    json_extract_string(fields, '$.category')                        AS category,
                    json_extract_string(fields, '$.source')                          AS source,
                    json_extract_string(fields, '$.attributes')                      AS attributes,
                    thread_hash
                FROM raised
                WHERE COALESCE(CAST(json_extract_string(fields, '$.traceId') AS BIGINT), 0) <> 0
            )
            SELECT n.trace_id, s.span_id, n.notification_id, n.start_timestamp, n.start_ms,
                   n.type, n.message_ref, n.severity, n.category, n.source, n.attributes,
                   n.thread_hash
            FROM identified n
            JOIN traces tr ON tr.trace_id = n.trace_id
            LEFT JOIN trace_spans s ON s.trace_id = n.trace_id AND s.span_id = n.span_id
            ORDER BY n.trace_id, n.start_timestamp
            """.replace("<<rehydrated>>", REHYDRATED_FIELDS);

    /*
     * The throws that happened inside a trace.
     *
     * Nothing new is ingested: jdk.JavaExceptionThrow and jdk.JavaErrorThrow are already parsed and
     * already power the Exceptions view. What is derived is the correlation, and unlike a
     * notification a throw needs no ids to correlate -- it is always recorded on the thread that
     * threw it, at the instant it threw. The span is the innermost one containing that instant on
     * that thread: the narrowest containing window wins, which is the same rule the promoted
     * blocking spans follow.
     *
     * The window comparison runs in microseconds because that is the resolution a span start is
     * kept at; `duration` is nanoseconds, hence the divide. A span of zero duration therefore
     * contains nothing, which is correct -- an instant cannot have happened inside it.
     *
     * `escaped` says this throw is why its span failed, decided by matching the thrown class
     * against the span's own error_type. A throw caught inside the span leaves it FALSE, which is
     * most of them.
     *
     * One throwable, one row -- which takes the `distinct_throws` step, because JFR does not record
     * these events once each. Both are emitted from constructors (jdk.jfr.internal.instrument
     * .ThrowableTracer, woven into Throwable and Error), and the Error hook commits an
     * ExceptionThrown of its own beside its ErrorThrown. So constructing an Error emits three
     * events -- two jdk.JavaExceptionThrow and one jdk.JavaErrorThrow -- while an ordinary
     * exception emits one. Counted raw, every Error is three throws and appears twice over, once
     * under each event type, as two findings that are really one.
     *
     * The de-duplication is by class rather than by instant: a class either extends Error or it
     * does not, so a class that produced any jdk.JavaErrorThrow keeps only those rows, and its
     * jdk.JavaExceptionThrow rows are the duplicate view of the same objects. Matching on the
     * timestamp instead would be wrong -- the Error hook and the Throwable hook read the clock
     * separately, so the twins are not guaranteed to share a tick. OutOfMemoryError is the one
     * Error the tracer skips, emitting no jdk.JavaErrorThrow at all; it produces no error rows to
     * anti-join against, so its single event survives, which is the right answer for it too.
     *
     * A recording with jdk.JavaErrorThrow disabled has nothing to de-duplicate against and its
     * errors stay double-counted. Nothing here can recover that -- the duplicate is
     * indistinguishable from a second throw -- and both event types travel together in every
     * settings file that enables either.
     */
    //language=SQL
    private static final String DERIVE_TRACE_EXCEPTIONS = """
            INSERT INTO trace_exceptions (trace_id, span_id, exception_id, start_timestamp,
                                          start_timestamp_from_beginning, event_type, thrown_class,
                                          message, escaped, stacktrace_hash, thread_hash)
            WITH thrown AS (
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
                  AND e.thread_hash IS NOT NULL
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
            -- class did not extract survives to be dropped by the NOT NULL filter below, instead of
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
            ),
            attributed AS (
                SELECT
                    x.exception_id,
                    x.event_type,
                    x.start_timestamp,
                    x.start_ms,
                    x.thread_hash,
                    x.stacktrace_hash,
                    x.thrown_class,
                    x.message,
                    s.trace_id                                     AS trace_id,
                    s.span_id                                      AS span_id,
                    s.error_type                                   AS error_type
                FROM distinct_throws x
                JOIN trace_spans s
                  ON s.thread_hash = x.thread_hash
                 AND EPOCH_US(s.start_timestamp) <= x.start_us
                 AND x.start_us < EPOCH_US(s.start_timestamp) + (s.duration / 1000)
                -- The innermost containing span: shortest window first, latest start to break a tie.
                QUALIFY ROW_NUMBER() OVER (PARTITION BY x.exception_id
                                           ORDER BY s.duration, s.start_timestamp DESC) = 1
            ),
            classified AS (
                SELECT
                    a.*,
                    -- error_type IS NOT NULL first: `x = NULL` is NULL, not false, and a NULL
                    -- would fail the column's NOT NULL rather than reading as "did not escape".
                    -- Most spans succeed, so most rows take exactly that branch.
                    a.error_type IS NOT NULL AND a.thrown_class = a.error_type AS escaped
                FROM attributed a
            )
            SELECT trace_id, span_id, exception_id, start_timestamp, start_ms, event_type,
                   thrown_class, message, escaped, stacktrace_hash, thread_hash
            FROM classified
            WHERE thrown_class IS NOT NULL
              -- `escaped` first, and never NULL: a throw that failed its span is the story
              -- whoever threw it, so only a caught resolution failure is ever dropped.
              AND (escaped OR thrown_class NOT IN (:resolution_failure_classes))
            ORDER BY trace_id, start_timestamp
            """.replace("<<rehydrated>>", REHYDRATED_FIELDS);

    //language=SQL
    private static final String TRACES_EXIST = """
            SELECT COUNT(*) FROM (SELECT 1 FROM traces LIMIT 1) probe
            """;

    /**
     * Matches one trace type. All three columns, never the name alone — see {@link TraceOperationId}
     * for why, and note that any query filtering on less than this silently re-merges what
     * {@link #OPERATIONS} just separated.
     */
    //language=SQL
    private static final String OPERATION_PREDICATE =
            "root_name = :root_name AND root_kind = :root_kind AND root_event_type = :root_event_type";

    /*
     * The trace header, in the one shape every list and detail reads it in. The three call sites
     * differ only in how they narrow and order it, which is what `%s` carries — they were three
     * copies of these ten columns, and a column added to one of them went missing from the others.
     */
    //language=SQL
    private static final String TRACE_SUMMARIES = """
            SELECT
                trace_id,
                root_name,
                root_kind,
                root_event_type,
                start_timestamp_from_beginning          AS start_ms,
                EPOCH_MS(start_timestamp)               AS start_epoch_ms,
                duration                                AS duration_ns,
                span_count,
                error_count,
                has_platform_span
            FROM traces
            %s
            """;

    // Both lists carry a trace_id tie-break: duration and start_timestamp can tie (the latter is
    // only microsecond-accurate), and a tied row at the LIMIT boundary would otherwise come and go
    // between two identical requests — which, once the list pages, would also let a row appear on
    // two pages or on neither.
    private static final String TRACE_LIST = TRACE_SUMMARIES + """
            ORDER BY %s, trace_id
                LIMIT :limit OFFSET :offset""";

    /**
     * How many traces a filter matches, for the "showing 50 of 3,214" the paged list needs. A second
     * statement rather than a window function beside the rows: the count has to survive the LIMIT
     * that the rows are subject to.
     */
    //language=SQL
    private static final String COUNT_TRACES = """
            SELECT COUNT(*) AS total FROM traces
            %s
            """;

    private static final String TRACES_OF_OPERATION = TRACE_SUMMARIES.formatted("""
            WHERE %s
                ORDER BY start_timestamp, trace_id
                LIMIT :limit""".formatted(OPERATION_PREDICATE));

    private static final String TRACE_BY_ID = TRACE_SUMMARIES.formatted("WHERE trace_id = :trace_id");

    /*
     * The start is projected as EPOCH_US, not EPOCH_MS: a span is routinely shorter than a
     * millisecond, so flooring its start to one puts sequential spans on the same instant and the
     * waterfall then draws them overlapping. Microseconds are all the stored timestamp carries.
     */
    //language=SQL
    private static final String SPANS_OF_TRACE = """
            SELECT
                s.trace_id                              AS trace_id,
                s.span_id                               AS span_id,
                s.parent_span_id                        AS parent_span_id,
                s.name                                  AS name,
                s.status                                AS status,
                s.kind                                  AS kind,
                s.error_type                            AS error_type,
                s.start_timestamp_from_beginning        AS start_ms,
                EPOCH_US(s.start_timestamp)             AS start_epoch_us,
                s.duration                              AS duration_ns,
                s.self_duration                         AS self_duration_ns,
                COALESCE(s.thread_hash, 0)              AS thread_hash,
                t.name                                  AS thread_name,
                COALESCE(t.is_virtual, FALSE)           AS is_virtual,
                s.event_type                            AS event_type,
                s.attributes                            AS attributes,
                p.payload                               AS event_fields,
                s.synthesized                           AS synthesized,
                s.io_origin                             AS io_origin
            FROM trace_spans s
            LEFT JOIN threads t ON s.thread_hash = t.thread_hash
            LEFT JOIN trace_span_payloads p ON p.payload_id = s.event_fields_ref
            WHERE s.trace_id = :trace_id
            ORDER BY s.start_timestamp
            """;

    /**
     * Which event types record a scope — a stretch of time one span was active on one thread.
     * Discovered structurally, the same way {@link #SPAN_EVENT_TYPES} is, so third-party
     * instrumentation that emits scopes takes part with no change here.
     * <p>
     * The marker is {@code scopedSpanId} rather than {@code spanId} precisely so that the two
     * queries cannot claim the same event type: a scope is not a span and has no place in the
     * waterfall.
     */
    //language=SQL
    private static final String SCOPE_EVENT_TYPES = """
            SELECT name FROM event_types
            WHERE list_contains(json_extract_string(columns, '$[*].field'), 'scopedSpanId')""";

    /*
     * The windows each of an operation's traces occupied on each thread, which is all the span-scoped
     * flamegraph needs from it.
     *
     * Reduced here rather than in Java: this used to fetch every span of every trace of the type --
     * unbounded, on the hot path of both the panel list and the flamegraph -- only for the manager to
     * collapse them and drop the rest. A hot operation materialised hundreds of thousands of span
     * records, each holding up to three strings, per request. The merge returns what survives that
     * reduction and nothing else.
     *
     * Overlapping and touching windows are merged per (trace, thread), but gaps between them are
     * preserved -- the gaps-and-islands pass below. A plain MIN/MAX per (trace, thread) was tried
     * first and quietly over-approximated: a thread active early and again late in a trace got one
     * window spanning its idle gap, and the operation flamegraph absorbed whatever unrelated work
     * that thread did in between. The row count is one per contiguous busy stretch instead of one
     * per (trace, thread) -- bounded by the operation's span and scope count, in practice close to
     * the old cardinality.
     *
     * The bounds stay in microseconds, the resolution the stored timestamp carries; crossing into
     * the events table's millisecond domain happens in one place in the manager, not here.
     *
     * Scope events are unioned in rather than the span's own thread being trusted alone. A span is
     * committed by the thread that *ends* it, so a re-entered span -- a gRPC call arriving in
     * callbacks -- names one thread while having run on several, and sampling only that one thread
     * produces an empty or misleading flamegraph. Each scope is bounded by a single re-entry lambda
     * and therefore cannot straddle a thread, so a cross-thread span contributes one true window per
     * thread it actually occupied. Spans that were never re-entered emit no scopes and are unchanged
     * by this: their own row is already the only window they have.
     *
     * Package-private because {@link SpanScopeSql} embeds it: an operation-scoped query derives these
     * windows inside itself rather than having them fetched and handed back as bind values. One
     * definition of "the windows this operation occupied" for both readers.
     */
    //language=SQL
    static final String OPERATION_INTERVALS = """
            WITH matched AS (
                SELECT s.trace_id, s.span_id, s.thread_hash, s.start_timestamp, s.duration
                FROM trace_spans s
                JOIN traces t ON t.trace_id = s.trace_id
                WHERE %s
            ),
            windows AS (
                SELECT
                    trace_id,
                    COALESCE(thread_hash, 0)                        AS thread_hash,
                    EPOCH_US(start_timestamp)                       AS from_us,
                    EPOCH_US(start_timestamp) + duration // 1000    AS to_us
                FROM matched
                UNION ALL
                SELECT
                    m.trace_id,
                    COALESCE(e.thread_hash, 0)                                  AS thread_hash,
                    EPOCH_US(e.start_timestamp)                                 AS from_us,
                    EPOCH_US(e.start_timestamp) + COALESCE(e.duration, 0) // 1000 AS to_us
                FROM events e
                JOIN matched m
                  ON m.trace_id = json_extract_string(e.fields, '$.traceId')::BIGINT
                 AND m.span_id = json_extract_string(e.fields, '$.scopedSpanId')::BIGINT
                WHERE e.event_type IN (%s)
            ),
            numbered AS (
                SELECT
                    trace_id, thread_hash, from_us, to_us,
                    CASE WHEN from_us > MAX(to_us) OVER (
                             PARTITION BY trace_id, thread_hash
                             ORDER BY from_us, to_us
                             ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING)
                         THEN 1 ELSE 0 END AS starts_island
                FROM windows
            ),
            islands AS (
                SELECT
                    trace_id, thread_hash, from_us, to_us,
                    SUM(starts_island) OVER (
                        PARTITION BY trace_id, thread_hash
                        ORDER BY from_us, to_us
                        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS island
                FROM numbered
            )
            SELECT
                thread_hash             AS thread_hash,
                MIN(from_us)            AS from_epoch_us,
                MAX(to_us)              AS to_epoch_us
            FROM islands
            GROUP BY trace_id, thread_hash, island
            """.formatted(OPERATION_PREDICATE, SCOPE_EVENT_TYPES);

    /*
     * One row of profile-wide totals. Every aggregate is COALESCEd because an untraced profile
     * leaves `traces` empty, where SUM, MAX and QUANTILE_CONT all return SQL NULL rather than zero.
     *
     * Both the total and the distinct count are taken off `traces`, keyed by root_name, so this
     * agrees with the Traces by Operation view: an operation is a trace type, not a span name.
     */
    //language=SQL
    private static final String OVERVIEW = """
            SELECT
                COUNT(*)                                                    AS total_traces,
                COALESCE(SUM(span_count), 0)                                AS total_spans,
                COUNT(*) FILTER (WHERE error_count > 0)                     AS error_traces,
                COALESCE(SUM(error_count), 0)                               AS error_spans,
                COALESCE(CAST(AVG(duration) AS BIGINT), 0)                  AS avg_ns,
                COALESCE(CAST(QUANTILE_CONT(duration, 0.95) AS BIGINT), 0)  AS p95_ns,
                COALESCE(CAST(QUANTILE_CONT(duration, 0.99) AS BIGINT), 0)  AS p99_ns,
                COALESCE(MAX(duration), 0)                                  AS max_ns,
                COALESCE(SUM(duration), 0)                                  AS total_ns,
                (SELECT COUNT(*) FROM (
                    SELECT DISTINCT root_name, root_kind, root_event_type FROM traces
                ))                                                          AS distinct_operations
            FROM traces
            """;

    /*
     * One row per trace type, keyed by the root span's name. Aggregated over `traces` rather than
     * `trace_spans` because an operation is a kind of trace: grouping spans would list names that
     * only ever appear nested, which no trace can be opened at.
     *
     * Grouped by the whole trace type rather than by the name with ANY_VALUE over the rest. An
     * inbound `GET /orders` and an outbound call to the same path are named identically by the same
     * convention; grouped by name they collapsed into one row whose kind and event-type badges were
     * whichever value the aggregate happened to sample, and whose count and percentiles mixed two
     * unrelated populations.
     */
    /** Groups the traces table into trace types; shared by the row query and its count. */
    //language=SQL
    private static final String OPERATION_GROUPING = """
            FROM traces
            %s
            GROUP BY root_name, root_kind, root_event_type
            %s
            """;

    //language=SQL
    private static final String OPERATIONS = """
            SELECT
                root_name                                           AS name,
                root_kind                                           AS kind,
                root_event_type                                     AS event_type,
                COUNT(*)                                            AS count,
                COUNT(*) FILTER (WHERE error_count > 0)             AS error_count,
                SUM(span_count)                                     AS span_count,
                SUM(duration)                                       AS total_ns,
                CAST(QUANTILE_CONT(duration, 0.5) AS BIGINT)        AS p50_ns,
                CAST(QUANTILE_CONT(duration, 0.95) AS BIGINT)       AS p95_ns,
                CAST(QUANTILE_CONT(duration, 0.99) AS BIGINT)       AS p99_ns,
                MAX(duration)                                       AS max_ns
            """ + OPERATION_GROUPING + """
            ORDER BY %s, root_name, root_kind, root_event_type
            LIMIT :limit OFFSET :offset
            """;

    /** How many trace types a filter matches — the operations counterpart of {@link #COUNT_TRACES}. */
    //language=SQL
    private static final String COUNT_OPERATIONS = """
            SELECT COUNT(*) AS total FROM (
                SELECT root_name, root_kind, root_event_type
            """ + OPERATION_GROUPING + """
            )
            """;

    /**
     * Only operations that failed at least once. A {@code HAVING}, not a {@code WHERE}: filtering the
     * traces first would drop the successful ones from the aggregates too, and report every surviving
     * operation as having a 100% error rate and a latency distribution taken only from its failures.
     */
    //language=SQL
    private static final String OPERATION_ERRORS_ONLY_HAVING =
            "HAVING COUNT(*) FILTER (WHERE error_count > 0) > 0";

    /*
     * How traces were spread over the recording, as equal slices of the stretch that actually holds
     * traces. The width is derived in SQL from that stretch rather than passed in, so the caller only
     * has to say how many slices it wants to draw.
     *
     * Max duration per bucket, not average: a bucket holding one slow trace among two hundred fast
     * ones is exactly the bucket worth opening, and an average buries it.
     *
     * The slot grid is generated and left-joined rather than letting GROUP BY decide which buckets
     * exist, so a stretch with no trace in it comes back as a zero instead of not coming back. The
     * difference matters to whoever draws the result: a line drawn through the buckets that happen
     * to exist slopes gently across a ten-minute silence, which reads as "traffic tailed off" rather
     * than "traffic stopped".
     *
     * Bounds are taken over every trace, never over the narrowing below, so one operation's shape is
     * drawn against the recording's own clock and two operations can be compared slot for slot.
     *
     * The `from_ms IS NOT NULL` guard is what keeps a profile with no traces at no buckets: without
     * it the bounds are null, GREATEST still yields a width, and the grid comes back as a row of
     * nulls per requested slot.
     */
    //language=SQL
    private static final String TIMELINE_TEMPLATE = """
            WITH bounds AS (
                SELECT
                    MIN(start_timestamp_from_beginning) AS from_ms,
                    MAX(start_timestamp_from_beginning) AS to_ms
                FROM traces
            ),
            slicing AS (
                SELECT
                    from_ms,
                    GREATEST(1, CAST(CEIL((to_ms - from_ms + 1) / CAST(:buckets AS DOUBLE)) AS BIGINT))
                        AS bucket_ms
                FROM bounds
                WHERE from_ms IS NOT NULL
            ),
            grid AS (
                SELECT s.from_ms + slot * s.bucket_ms AS bucket_from_ms
                FROM slicing s, range(0, CAST(:buckets AS BIGINT)) AS slots(slot)
            ),
            occupied AS (
                SELECT
                    s.from_ms + CAST(FLOOR((t.start_timestamp_from_beginning - s.from_ms) / s.bucket_ms)
                        AS BIGINT) * s.bucket_ms                    AS bucket_from_ms,
                    COUNT(*)                                        AS count,
                    COUNT(*) FILTER (WHERE t.error_count > 0)       AS error_count,
                    MAX(t.duration)                                 AS max_ns
                FROM traces t, slicing s
                %s
                GROUP BY bucket_from_ms
            )
            SELECT
                g.bucket_from_ms                                    AS bucket_from_ms,
                COALESCE(o.count, 0)                                AS count,
                COALESCE(o.error_count, 0)                          AS error_count,
                COALESCE(o.max_ns, 0)                               AS max_ns
            FROM grid g
            LEFT JOIN occupied o ON o.bucket_from_ms = g.bucket_from_ms
            ORDER BY g.bucket_from_ms
            """;

    private static final String TIMELINE = TIMELINE_TEMPLATE.formatted("");

    /**
     * The same shape for one trace type. Narrowed in SQL rather than by bucketing what the trace list
     * fetched: that list is capped and ordered by start time, so bucketing it plots the recording's
     * first thousand traces and labels the result an hour.
     */
    private static final String TIMELINE_OF_OPERATION =
            TIMELINE_TEMPLATE.formatted("WHERE " + OPERATION_PREDICATE);

    /*
     * Where an operation spends its time, one row per span name across every trace of the type.
     *
     * Two totals, because they answer different questions. The inclusive one contains the span's
     * children, so the rows sum past the operation's own duration -- it says which part of the tree
     * a request is inside. The self one contains only the span's own work, so the rows sum to the
     * operation's time and can be ranked against each other -- it says which code to go and look at.
     * Ranking by the two routinely disagrees, which is the point of carrying both: a span that
     * merely wraps three slow queries tops the inclusive list and barely registers on the self one.
     *
     * Self time is summed from the stored column rather than recomputed here: the interval merge it
     * comes from runs once at derivation, where every span of a trace is in hand.
     *
     * The trace's own root is excluded by span id, not by name. By name, an operation that calls
     * itself -- a handler that recurses, a retry that re-enters the same path -- matched every one
     * of its nested occurrences too and dropped them all, so the breakdown of the one operation most
     * worth breaking down came back missing its own recursion.
     *
     * Grouped by event type as well as by name, because the type is what the row is *made of*: a
     * `jdk.SocketRead` the derivation promoted and a query the application instrumented are a wait
     * and a call, and the breakdown says which without the reader having to recognise the name. One
     * name under two types is then two rows rather than one row wearing whichever type won -- which
     * is also the honest answer, since they are two different things that happen to share a name.
     */
    //language=SQL
    private static final String SPAN_BREAKDOWN_OF_OPERATION = """
            SELECT
                s.name                                              AS name,
                s.event_type                                        AS event_type,
                COUNT(*)                                            AS occurrences,
                COUNT(DISTINCT s.trace_id)                          AS trace_count,
                SUM(s.duration)                                     AS total_ns,
                SUM(s.self_duration)                                AS self_ns,
                CAST(QUANTILE_CONT(s.duration, 0.5) AS BIGINT)      AS p50_ns,
                CAST(QUANTILE_CONT(s.self_duration, 0.5) AS BIGINT) AS p50_self_ns,
                CAST(QUANTILE_CONT(s.duration, 0.99) AS BIGINT)     AS p99_ns,
                CAST(QUANTILE_CONT(s.self_duration, 0.99) AS BIGINT) AS p99_self_ns,
                MAX(s.duration)                                     AS max_ns
            FROM trace_spans s
            JOIN traces t ON t.trace_id = s.trace_id
            WHERE %s
              AND s.span_id <> t.root_span_id
            GROUP BY s.name, s.event_type
            ORDER BY total_ns DESC, name
            LIMIT :limit
            """.formatted(OPERATION_PREDICATE);

    /*
     * The platform/virtual split of an operation's spans, which decides whether any of its work can
     * carry a flamegraph at all -- samples are attributed to the carrier, never the virtual thread.
     *
     * Three buckets, not two: a span whose thread did not resolve is neither, and folding it into
     * the platform count -- which `NOT COALESCE(is_virtual, FALSE)` did -- turned "we do not know"
     * into "samples are available here" and promised a flamegraph that comes back empty. The same
     * convention as `traces.has_platform_span`.
     */
    //language=SQL
    private static final String THREADS_OF_OPERATION = """
            SELECT
                COUNT(DISTINCT s.thread_hash)                       AS distinct_threads,
                COUNT(*) FILTER (WHERE th.is_virtual = FALSE)       AS platform_spans,
                COUNT(*) FILTER (WHERE th.is_virtual = TRUE)        AS virtual_spans,
                COUNT(*) FILTER (WHERE th.is_virtual IS NULL)       AS unknown_spans
            FROM trace_spans s
            JOIN traces t ON t.trace_id = s.trace_id
            LEFT JOIN threads th ON th.thread_hash = s.thread_hash
            WHERE %s
            """.formatted(OPERATION_PREDICATE);

    /**
     * What ran on the span's thread while it was open, minus the events that describe the trace
     * itself — spans, which belong in the waterfall rather than in the list of what happened inside
     * one; scopes, which only say where a span was running and would read as noise beside a
     * lock or an I/O event; and the blocking events the derivation promoted, each of which is
     * already a child bar of this very span and would otherwise appear twice.
     * <p>
     * NOT EXISTS rather than NOT IN: a NOT IN whose subquery yields a single NULL matches nothing at
     * all, so the drill-down would return zero events rather than fail. {@code event_types.name} is
     * NOT NULL today, which is the only reason that never happened. The promoted list is a plain
     * NOT IN because its values are compile-time constants, never NULL.
     */
    private static final String EVENTS_IN_SPAN = ThreadWindowEvents.excluding("""
            NOT EXISTS (SELECT 1 FROM (%s) span_types WHERE span_types.name = e.event_type)
            AND NOT EXISTS (SELECT 1 FROM (%s) scope_types WHERE scope_types.name = e.event_type)
            AND e.event_type NOT IN (%s)
            AND e.event_type NOT IN (%s)"""
            .formatted(SPAN_EVENT_TYPES, SCOPE_EVENT_TYPES, PromotedSpans.sqlQuotedEventTypes(),
                    "'" + EventTypeName.NOTIFICATION + "', " + THROW_EVENT_TYPES_SQL));

    /*
     * How the recording described each field of an event type. `columns` is a JSON array the parser
     * copied out of the recording's metadata, so unnesting it here means the label, description and
     * content type reach the UI without anyone hand-maintaining a table of them.
     *
     * The content type is JFR's own formatting annotation -- jdk.jfr.DataAmount, jdk.jfr.Timespan
     * and so on -- which is what lets a byte count render as a byte count for every event type at
     * once, including ones instrumented after this query was written.
     */
    //language=SQL
    private static final String EVENT_FIELDS = """
            SELECT
                et.name                                        AS event_type,
                json_extract_string(f.value, '$.field')        AS field,
                json_extract_string(f.value, '$.header')       AS label,
                json_extract_string(f.value, '$.description')  AS description,
                json_extract_string(f.value, '$.type')         AS content_type
            FROM event_types et,
                 UNNEST(CAST(json_extract(et.columns, '$[*]') AS JSON[])) AS f(value)
            WHERE et.name IN (:event_types)
            """;

    /**
     * How far back a pause is allowed to have started before the window it is being looked for in.
     * <p>
     * Overlap is not sargable on the lower bound — "started before this instant and had not ended"
     * cannot be answered from a start timestamp alone — so without a floor the query would scan the
     * events table from the beginning of the recording. A pause longer than this is a JVM in far
     * more trouble than a trace view is going to diagnose.
     */
    private static final long MAX_PAUSE_LOOKBACK_MILLIS = 60_000L;

    private static final long MICROS_PER_MILLI_LONG = 1_000L;

    /*
     * The stop-the-world stretches overlapping a window, whichever thread recorded them.
     *
     * No thread predicate at all, which is the whole point: a collection pause and a safepoint are
     * emitted on a VM thread and stop every application thread, so matching a span's own thread hash
     * — what every other window query in this layer does — finds none of them, ever.
     *
     * Overlap, not starts-inside: a 40ms pause that began 5ms before the span is exactly the one
     * worth drawing, and the starts-inside predicate the thread drill-down uses would miss it. The
     * lower bound is floored by MAX_PAUSE_LOOKBACK_MILLIS so the scan still prunes on the same zone
     * maps; the upper bound keeps the sargable form ThreadWindowEvents documents.
     *
     * A label is read out of the event's own fields, trying the names those event types use, so a
     * band can say "G1 Young" or "RevokeBias" rather than only that something stopped the world.
     *
     * The duration is handed over in nanoseconds exactly as recorded. It used to be folded into a
     * microsecond end timestamp and rebuilt from the difference, which floored every pause shorter
     * than a microsecond to nothing at all — and the levelled GC phases are full of them.
     *
     * Events with no duration are dropped rather than defaulted to zero. JFR writes some phases with
     * a null duration ("Notify and keep alive finalizable" among them); a pause of unknown length is
     * not a stretch of stopped time, and a zero-length one still draws at the band floor, so
     * defaulting it asserted a pause the recording never claimed.
     */
    //language=SQL
    private static final String PAUSES_IN_WINDOW = """
            SELECT
                e.event_type                                        AS event_type,
                COALESCE(
                    json_extract_string(e.fields, '$.name'),
                    json_extract_string(e.fields, '$.operation'),
                    json_extract_string(e.fields, '$.cause'),
                    e.event_type)                                   AS label,
                EPOCH_US(e.start_timestamp)                         AS from_epoch_us,
                e.duration                                          AS duration_ns
            FROM events e
            WHERE e.event_type IN (:pause_event_types)
                AND e.duration IS NOT NULL
                AND e.start_timestamp >= make_timestamptz(:lookback_from_ms * 1000)
                AND e.start_timestamp < make_timestamptz((:to_ms + 1) * 1000)
                AND EPOCH_US(e.start_timestamp) + e.duration // 1000 > :from_us
            ORDER BY e.start_timestamp
            LIMIT :limit
            """;

    /*
     * How far outside the trace the throttle scan reaches for the samples that bound its windows.
     *
     * Applied in both directions, and it has to be. A window is a *pair* of samples, so the one
     * overlapping a 300ms trace is typically opened by a sample taken well before it and closed by
     * one taken well after -- and a scan bounded by the trace itself would drop both, leaving the
     * LAG nothing to pair and losing exactly the window that explains the trace.
     * jdk.ContainerCPUThrottling is emitted on the everyChunk period (30s by default), so two
     * default periods of slack keeps the pairing intact on either side without widening the scan
     * enough to stop the zone maps pruning it.
     */
    private static final long MAX_THROTTLE_SAMPLE_GAP_MILLIS = 120_000L;

    /*
     * The CFS sampling windows that contained throttling and overlap a trace.
     *
     * Every other context query in this layer reads a stretch of time straight off an event. This
     * one cannot: the counters are cumulative since the cgroup was created, so no row describes the
     * stretch between two samples. LAG rebuilds each window from the pair that bounds it, and what
     * comes out is the *window*, not the throttling -- somewhere inside it the kernel parked the
     * container for delta_throttled_ns, and nothing here or anywhere else says where.
     *
     * A negative delta means the counters were reset under us -- a restarted container reusing the
     * profile -- and the window is dropped rather than clamped to zero: how much of the throttling
     * preceded the reset is not recoverable, and a clamped window would report a number that is
     * merely wrong instead of absent. Requiring delta_elapsed > 0 does the same for a window in
     * which the CFS clock did not advance, which is what a container with no quota looks like once
     * its null counters are excluded.
     *
     * Overlap rather than starts-inside, for the same reason the pause query uses it: a window that
     * opened before the trace is exactly the one explaining its first slow span.
     */
    //language=SQL
    private static final String THROTTLE_WINDOWS_IN_WINDOW = """
            WITH samples AS (
                SELECT
                    EPOCH_US(e.start_timestamp)                                  AS at_us,
                    TRY_CAST(json_extract_string(e.fields, '$.%s') AS BIGINT)    AS elapsed,
                    TRY_CAST(json_extract_string(e.fields, '$.%s') AS BIGINT)    AS throttled,
                    TRY_CAST(json_extract_string(e.fields, '$.%s') AS BIGINT)    AS throttled_ns
                FROM events e
                WHERE e.event_type = :throttle_event_type
                    AND e.start_timestamp >= make_timestamptz(:scan_from_ms * 1000)
                    AND e.start_timestamp < make_timestamptz((:scan_to_ms + 1) * 1000)
            ),
            counted AS (
                SELECT at_us, elapsed, throttled, throttled_ns
                FROM samples
                WHERE elapsed IS NOT NULL
                    AND throttled IS NOT NULL
                    AND throttled_ns IS NOT NULL
            ),
            windows AS (
                SELECT
                    LAG(at_us)        OVER (ORDER BY at_us)                 AS from_us,
                    at_us                                                   AS to_us,
                    elapsed      - LAG(elapsed)      OVER (ORDER BY at_us)  AS delta_elapsed,
                    throttled    - LAG(throttled)    OVER (ORDER BY at_us)  AS delta_throttled,
                    throttled_ns - LAG(throttled_ns) OVER (ORDER BY at_us)  AS delta_throttled_ns
                FROM counted
            )
            SELECT
                from_us                 AS from_epoch_us,
                to_us                   AS to_epoch_us,
                delta_elapsed           AS elapsed_slices,
                delta_throttled         AS throttled_slices,
                delta_throttled_ns      AS throttled_ns
            FROM windows
            WHERE from_us IS NOT NULL
                AND delta_elapsed > 0
                AND delta_throttled > 0
                AND delta_throttled_ns >= 0
                AND to_us > :from_us
                AND from_us < :to_us
            ORDER BY from_us
            LIMIT :limit
            """.formatted(
                    ThrottleCounters.ELAPSED_SLICES,
                    ThrottleCounters.THROTTLED_SLICES,
                    ThrottleCounters.THROTTLED_TIME);

    /*
     * What each span of a trace spent waiting on, one row per (span, category) that recorded
     * anything.
     *
     * Joined on the span's own thread and window, because these events are thread-scoped: a lock the
     * request waited on was contended on the request's thread. Events are attributed to the span
     * whose window they start in — the *innermost* one, which the QUALIFY enforces per event: the
     * window join alone matches every enclosing span on the thread, and without the pick a wait
     * inside a nested span was charged to the child, its parent, and the summary's total all at
     * once. Innermost is what makes these totals comparable to self time.
     *
     * The event types the derivation promotes into synthesized leaf spans never reach this query --
     * ThreadCategoryMapping leaves them out -- because a promoted wait already is a bar under its
     * span, and counting it here again would say the same blocked microsecond twice.
     *
     * The category mapping arrives as a two-list UNNEST rather than a CASE ladder over event type
     * names, so the enum stays the only place the mapping is written down.
     */
    //language=SQL
    private static final String SPAN_CONTEXT = """
            WITH mapping AS (
                SELECT UNNEST([:context_event_types]) AS event_type,
                       UNNEST([:context_categories])  AS category
            ),
            spans AS (
                SELECT
                    span_id,
                    thread_hash,
                    EPOCH_US(start_timestamp)                       AS from_us,
                    EPOCH_US(start_timestamp) + duration // 1000    AS to_us
                FROM trace_spans
                WHERE trace_id = :trace_id
                  AND thread_hash IS NOT NULL
                  -- Context explains recorded spans; a synthesized leaf IS context already, and
                  -- letting one be the innermost candidate would charge a deoptimization that
                  -- fired during a socket read to the wait instead of to the span doing the work.
                  AND NOT synthesized
            ),
            attributed AS (
                SELECT
                    s.span_id                                       AS span_id,
                    m.category                                      AS category,
                    COALESCE(e.duration, 0)                         AS duration_ns
                -- events_raw rather than the events view: rowid only exists on the physical table.
                -- No rehydration needed here, unlike the promotion above -- this reads a duration
                -- and a type, never the event's fields.
                FROM events_raw e
                JOIN mapping m ON m.event_type = e.event_type
                JOIN spans s
                  ON s.thread_hash = e.thread_hash
                 AND EPOCH_US(e.start_timestamp) >= s.from_us
                 AND EPOCH_US(e.start_timestamp) <= s.to_us
                WHERE e.event_type IN (:context_event_types)
                QUALIFY ROW_NUMBER() OVER (
                    PARTITION BY e.rowid
                    ORDER BY s.from_us DESC, s.to_us ASC, s.span_id) = 1
            )
            SELECT
                span_id                                             AS span_id,
                category                                            AS category,
                SUM(duration_ns)                                    AS total_ns,
                COUNT(*)                                            AS occurrences
            FROM attributed
            GROUP BY span_id, category
            """;

    private final DatabaseClient databaseClient;

    public JdbcTraceRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(PROFILE_TRACES);
    }

    @Override
    public void derive() {
        // Every one of these tables is wholly a function of `events`, so deriving twice must land
        // where deriving once did. Without this a re-run doubled every span and then failed on the
        // traces primary key, leaving the profile with spans that no trace header accounts for.
        databaseClient.execute(StatementLabel.DERIVE_TRACE_EXCEPTIONS, DELETE_TRACE_EXCEPTIONS);
        databaseClient.execute(StatementLabel.DERIVE_TRACE_NOTIFICATIONS, DELETE_TRACE_NOTIFICATIONS);
        databaseClient.execute(
                StatementLabel.DERIVE_TRACE_NOTIFICATIONS, DELETE_TRACE_NOTIFICATION_MESSAGES);
        databaseClient.execute(StatementLabel.DERIVE_TRACES, DELETE_TRACES);
        databaseClient.execute(StatementLabel.DERIVE_TRACE_SPANS, DELETE_TRACE_SPANS);
        databaseClient.execute(StatementLabel.DERIVE_TRACE_SPANS, DELETE_SPAN_PAYLOADS);

        MapSqlParameterSource promotionParams = new MapSqlParameterSource()
                .addValue("blocking_event_types", BlockingLeafSpans.eventTypes())
                .addValue("blocking_names", BlockingLeafSpans.names())
                .addValue("blocking_kinds", BlockingLeafSpans.kinds())
                .addValue("method_event_type", MethodSpans.EVENT_TYPE)
                .addValue("class_loading_event_types", ClassLoadingFrames.EVENT_TYPES)
                .addValue("class_loading_frame_prefixes", ClassLoadingFrames.CLASS_NAME_PREFIXES)
                .addValue("class_loading_origin", ClassLoadingFrames.CLASS_LOADING_ORIGIN)
                .addValue("promoted_event_types", List.copyOf(PromotedSpans.EVENT_TYPES));

        // Before the spans: the span insert stores payload references, and every reference it
        // computes must have its text row in place.
        databaseClient.insert(
                StatementLabel.DERIVE_TRACE_SPANS,
                DERIVE_SPAN_PAYLOADS.formatted(
                        EVENT_FIELDS_PROJECTION,
                        SPAN_EVENT_TYPES,
                        JDK_EVENT_PLUMBING_KEYS),
                promotionParams);

        // One template per event type -- built-ins overlaid by what the recording declares for
        // itself (@Span, stored in event_types.extras by the parser) -- rendered as one CASE.
        String nameTemplates = SpanNameTemplates.nameCase(databaseClient);

        // The placeholders in the order they appear: which event types are spans, the three span
        // shape projections, the event_fields stripping projection of the recorded branch, the
        // method span's self-naming projection, and the narrower plumbing strip of the promoted
        // branch.
        databaseClient.insert(
                StatementLabel.DERIVE_TRACE_SPANS,
                DERIVE_TRACE_SPANS.formatted(
                        SPAN_EVENT_TYPES,
                        SpanConventions.nameProjection(nameTemplates),
                        SpanConventions.kindProjection(),
                        SpanConventions.statusProjection(),
                        EVENT_FIELDS_PROJECTION,
                        MethodSpans.nameAndKindProjection(),
                        JDK_EVENT_PLUMBING_KEYS),
                promotionParams);

        databaseClient.execute(StatementLabel.DERIVE_TRACES, DERIVE_TRACES);

        // After the spans and the trace headers, which both of these join against: a notification
        // resolves its recorded span id through trace_spans, and a throw finds its span by walking
        // the windows on its own thread.
        // The message dictionary first: the notification insert computes each reference by hashing the
        // same text, so every reference it writes already has its row.
        databaseClient.insert(
                StatementLabel.DERIVE_TRACE_NOTIFICATIONS,
                DERIVE_TRACE_NOTIFICATION_MESSAGES,
                new MapSqlParameterSource().addValue("notification_event_type", EventTypeName.NOTIFICATION));

        databaseClient.insert(
                StatementLabel.DERIVE_TRACE_NOTIFICATIONS,
                DERIVE_TRACE_NOTIFICATIONS,
                new MapSqlParameterSource().addValue("notification_event_type", EventTypeName.NOTIFICATION));

        databaseClient.insert(
                StatementLabel.DERIVE_TRACE_EXCEPTIONS,
                DERIVE_TRACE_EXCEPTIONS,
                new MapSqlParameterSource()
                        .addValue("exception_event_types", THROW_EVENT_TYPES)
                        .addValue("error_throw_event_type", EventTypeName.JAVA_ERROR_THROW)
                        .addValue("resolution_failure_classes", RESOLUTION_FAILURE_CLASSES));
    }

    @Override
    public boolean hasTraces() {
        return databaseClient.queryExists(StatementLabel.TRACES_EXIST, TRACES_EXIST, new MapSqlParameterSource());
    }

    @Override
    public boolean hasSpanEventTypes() {
        return databaseClient.queryExists(
                StatementLabel.SPAN_EVENT_TYPES_EXIST, SPAN_EVENT_TYPES_EXIST, new MapSqlParameterSource());
    }

    @Override
    public TracePage traces(TraceListQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = traceFilter(query, params);

        long total = databaseClient.querySingle(
                        StatementLabel.COUNT_TRACES,
                        COUNT_TRACES.formatted(where),
                        params,
                        (rs, _) -> rs.getLong("total"))
                .orElse(0L);
        if (total == 0) {
            return TracePage.EMPTY;
        }

        // Added after the count, which must not carry them: a count with an OFFSET past the last row
        // returns nothing, and the page would report a filter matching nothing at all.
        params.addValue("limit", query.limit());
        params.addValue("offset", query.offset());

        List<TraceSummaryRecord> traces = databaseClient.query(
                StatementLabel.LIST_TRACES,
                TRACE_LIST.formatted(where, orderBy(query.sort().column(), query.descending())),
                params,
                traceSummaryMapper());
        return new TracePage(traces, total);
    }

    /**
     * The {@code WHERE} clause for a trace filter, binding each value it uses.
     * <p>
     * Built from the predicates that are actually active rather than from one clause per field
     * guarded by {@code :param IS NULL}: an unused bind parameter still has to be given a type, and
     * an untyped SQL NULL in a comparison is exactly the kind of thing that behaves differently
     * across engines. Only the fragments below reach the statement, and every value in them is bound.
     */
    private static String traceFilter(TraceListQuery query, MapSqlParameterSource params) {
        List<String> predicates = new ArrayList<>();
        if (query.hasNameFilter()) {
            predicates.add("root_name ILIKE :name_pattern ESCAPE '\\'");
            params.addValue("name_pattern", containsPattern(query.nameContains()));
        }
        if (query.errorsOnly()) {
            predicates.add("error_count > 0");
        }
        if (query.minDurationNanos() > 0) {
            predicates.add("duration >= :min_duration");
            params.addValue("min_duration", query.minDurationNanos());
        }
        if (query.operation() != null) {
            predicates.add(OPERATION_PREDICATE);
            params.addValue("root_name", query.operation().name())
                    .addValue("root_kind", query.operation().kind())
                    .addValue("root_event_type", query.operation().eventType());
        }
        return whereClause(predicates);
    }

    private static String whereClause(List<String> predicates) {
        if (predicates.isEmpty()) {
            return "";
        }
        return "WHERE " + String.join(" AND ", predicates);
    }

    /**
     * A {@code LIKE} pattern matching the term anywhere in the value.
     * <p>
     * The term's own wildcards are escaped first, so searching for {@code GET /a_b} looks for that
     * name rather than for any character where the underscore is — and a term of nothing but
     * {@code %} narrows to nothing instead of matching the table.
     */
    private static String containsPattern(String term) {
        String escaped = term
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * An {@code ORDER BY} expression with its direction. The expression is never caller-supplied —
     * it comes from {@link TraceSortField} or {@link TraceOperationSortField}, which exist precisely
     * because a column cannot be a bind parameter.
     */
    private static String orderBy(String expression, boolean descending) {
        return descending ? expression + " DESC" : expression;
    }

    @Override
    public List<TraceSummaryRecord> tracesOfOperation(TraceOperationId operation, int limit) {
        MapSqlParameterSource params = operationParams(operation).addValue("limit", limit);

        return databaseClient.query(
                StatementLabel.TRACE_OPERATION_TRACES,
                TRACES_OF_OPERATION,
                params,
                traceSummaryMapper());
    }

    @Override
    public Optional<TraceSummaryRecord> summaryOf(long traceId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("trace_id", traceId);

        return databaseClient.querySingle(
                StatementLabel.LIST_TRACES,
                TRACE_BY_ID,
                params,
                traceSummaryMapper());
    }

    @Override
    public List<TraceSpanRecord> spansOf(long traceId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("trace_id", traceId);

        return databaseClient.query(
                StatementLabel.TRACE_SPANS,
                SPANS_OF_TRACE,
                params,
                traceSpanMapper());
    }

    @Override
    public List<TraceNotificationRecord> notificationsOf(long traceId) {
        return databaseClient.query(
                StatementLabel.TRACE_NOTIFICATIONS,
                NOTIFICATIONS_OF_TRACE,
                new MapSqlParameterSource().addValue("trace_id", traceId),
                (rs, _) -> new TraceNotificationRecord(
                        rs.getLong("trace_id"),
                        JdbcNulls.longOrNull(rs, "span_id"),
                        rs.getLong("notification_id"),
                        rs.getLong("start_ms"),
                        rs.getLong("start_epoch_us"),
                        rs.getString("type"),
                        rs.getString("message"),
                        rs.getString("severity"),
                        rs.getString("category"),
                        rs.getString("source"),
                        rs.getString("attributes"),
                        rs.getLong("thread_hash")));
    }

    @Override
    public List<TraceExceptionRecord> exceptionsOf(long traceId) {
        return databaseClient.query(
                StatementLabel.TRACE_EXCEPTIONS,
                EXCEPTIONS_OF_TRACE,
                new MapSqlParameterSource().addValue("trace_id", traceId),
                (rs, _) -> new TraceExceptionRecord(
                        rs.getLong("trace_id"),
                        rs.getLong("span_id"),
                        rs.getLong("exception_id"),
                        rs.getLong("start_ms"),
                        rs.getLong("start_epoch_us"),
                        rs.getString("event_type"),
                        rs.getString("thrown_class"),
                        rs.getString("message"),
                        rs.getBoolean("escaped"),
                        JdbcNulls.longOrNull(rs, "stacktrace_hash"),
                        rs.getLong("thread_hash")));
    }

    @Override
    public List<EventFrame> stacktraceOf(long stacktraceHash) {
        return databaseClient.query(
                StatementLabel.TRACE_STACKTRACE,
                STACKTRACE_FRAMES,
                new MapSqlParameterSource().addValue("stacktrace_hash", stacktraceHash),
                (rs, _) -> new EventFrame(
                        rs.getString("class_name"),
                        rs.getString("method_name"),
                        rs.getString("frame_type"),
                        rs.getLong("bytecode_index"),
                        rs.getLong("line_number"),
                        rs.getString("hidden_class_id")));
    }

    @Override
    public List<SpanInterval> operationIntervals(TraceOperationId operation) {
        return databaseClient.query(
                StatementLabel.TRACE_OPERATION_SPANS,
                OPERATION_INTERVALS,
                operationParams(operation),
                (rs, _) -> new SpanInterval(
                        rs.getLong("thread_hash"),
                        Math.floorDiv(rs.getLong("from_epoch_us"), MICROS_PER_MILLI),
                        Math.floorDiv(rs.getLong("to_epoch_us"), MICROS_PER_MILLI)));
    }

    private static MapSqlParameterSource operationParams(TraceOperationId operation) {
        return new MapSqlParameterSource()
                .addValue("root_name", operation.name())
                .addValue("root_kind", operation.kind())
                .addValue("root_event_type", operation.eventType());
    }

    /** The projection shared by every read of a trace header — same columns, different narrowing. */
    private static RowMapper<TraceSummaryRecord> traceSummaryMapper() {
        return (rs, _) -> new TraceSummaryRecord(
                rs.getLong("trace_id"),
                rs.getString("root_name"),
                rs.getString("root_kind"),
                rs.getString("root_event_type"),
                rs.getLong("start_ms"),
                rs.getLong("start_epoch_ms"),
                rs.getLong("duration_ns"),
                rs.getInt("span_count"),
                rs.getInt("error_count"),
                rs.getBoolean("has_platform_span"));
    }

    /** The projection every span read shares — same columns, different {@code WHERE} clause. */
    private static RowMapper<TraceSpanRecord> traceSpanMapper() {
        return (rs, _) -> new TraceSpanRecord(
                rs.getLong("trace_id"),
                rs.getLong("span_id"),
                JdbcNulls.longOrNull(rs, "parent_span_id"),
                rs.getString("name"),
                rs.getString("kind"),
                rs.getString("status"),
                rs.getString("error_type"),
                rs.getLong("start_ms"),
                rs.getLong("start_epoch_us"),
                rs.getLong("duration_ns"),
                rs.getLong("self_duration_ns"),
                rs.getLong("thread_hash"),
                rs.getString("thread_name"),
                rs.getBoolean("is_virtual"),
                rs.getString("event_type"),
                rs.getString("attributes"),
                rs.getString("event_fields"),
                rs.getBoolean("synthesized"),
                rs.getString("io_origin"));
    }

    @Override
    public TraceOverviewRecord overview() {
        return databaseClient.querySingle(
                        StatementLabel.TRACE_OVERVIEW,
                        OVERVIEW,
                        new MapSqlParameterSource(),
                        (rs, _) -> new TraceOverviewRecord(
                                rs.getLong("total_traces"),
                                rs.getLong("total_spans"),
                                rs.getLong("error_traces"),
                                rs.getLong("error_spans"),
                                rs.getLong("avg_ns"),
                                rs.getLong("p95_ns"),
                                rs.getLong("p99_ns"),
                                rs.getLong("max_ns"),
                                rs.getLong("total_ns"),
                                rs.getInt("distinct_operations")))
                .orElse(TraceOverviewRecord.EMPTY);
    }

    @Override
    public TraceOperationPage operations(TraceOperationListQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();

        List<String> predicates = new ArrayList<>();
        if (query.hasNameFilter()) {
            predicates.add("root_name ILIKE :name_pattern ESCAPE '\\'");
            params.addValue("name_pattern", containsPattern(query.nameContains()));
        }
        String where = whereClause(predicates);
        String having = query.errorsOnly() ? OPERATION_ERRORS_ONLY_HAVING : "";

        long total = databaseClient.querySingle(
                        StatementLabel.COUNT_TRACE_OPERATIONS,
                        COUNT_OPERATIONS.formatted(where, having),
                        params,
                        (rs, _) -> rs.getLong("total"))
                .orElse(0L);
        if (total == 0) {
            return TraceOperationPage.EMPTY;
        }

        params.addValue("limit", query.limit());
        params.addValue("offset", query.offset());

        List<TraceOperationRecord> operations = databaseClient.query(
                StatementLabel.TRACE_OPERATIONS,
                OPERATIONS.formatted(
                        where, having, orderBy(query.sort().expression(), query.descending())),
                params,
                (rs, _) -> new TraceOperationRecord(
                        rs.getString("name"),
                        rs.getString("kind"),
                        rs.getString("event_type"),
                        rs.getLong("count"),
                        rs.getLong("error_count"),
                        rs.getLong("span_count"),
                        rs.getLong("total_ns"),
                        rs.getLong("p50_ns"),
                        rs.getLong("p95_ns"),
                        rs.getLong("p99_ns"),
                        rs.getLong("max_ns")));
        return new TraceOperationPage(operations, total);
    }

    @Override
    public List<TracePauseRecord> pausesInWindow(long fromEpochMicros, long toEpochMicros) {
        List<String> eventTypes = List.copyOf(
                TraceContextCategory.eventTypesOf(
                        TraceContextCategory.Scope.GLOBAL,
                        TraceContextCategory.Derivation.EVENT_DURATION));

        long toMillis = Math.floorDiv(toEpochMicros, MICROS_PER_MILLI_LONG);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pause_event_types", eventTypes)
                .addValue("from_us", fromEpochMicros)
                .addValue("to_ms", toMillis)
                .addValue("lookback_from_ms",
                        Math.floorDiv(fromEpochMicros, MICROS_PER_MILLI_LONG) - MAX_PAUSE_LOOKBACK_MILLIS)
                .addValue("limit", ThreadWindowEvents.ROW_LIMIT);

        return databaseClient.query(
                StatementLabel.TRACE_PAUSES,
                PAUSES_IN_WINDOW,
                params,
                (rs, _) -> {
                    String eventType = rs.getString("event_type");
                    return new TracePauseRecord(
                            TraceContextCategory.fromEventType(eventType).orElseThrow(),
                            rs.getString("label"),
                            rs.getLong("from_epoch_us"),
                            rs.getLong("duration_ns"),
                            TraceContextCategory.isDetail(eventType));
                });
    }

    @Override
    public List<TraceThrottleWindowRecord> throttledWindowsIn(long fromEpochMicros, long toEpochMicros) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("throttle_event_type", EventTypeName.CONTAINER_CPU_THROTTLING)
                .addValue("from_us", fromEpochMicros)
                .addValue("to_us", toEpochMicros)
                .addValue("scan_from_ms",
                        Math.floorDiv(fromEpochMicros, MICROS_PER_MILLI_LONG) - MAX_THROTTLE_SAMPLE_GAP_MILLIS)
                .addValue("scan_to_ms",
                        Math.floorDiv(toEpochMicros, MICROS_PER_MILLI_LONG) + MAX_THROTTLE_SAMPLE_GAP_MILLIS)
                .addValue("limit", ThreadWindowEvents.ROW_LIMIT);

        return databaseClient.query(
                StatementLabel.TRACE_THROTTLE_WINDOWS,
                THROTTLE_WINDOWS_IN_WINDOW,
                params,
                (rs, _) -> new TraceThrottleWindowRecord(
                        rs.getLong("from_epoch_us"),
                        rs.getLong("to_epoch_us"),
                        rs.getLong("throttled_ns"),
                        rs.getLong("throttled_slices"),
                        rs.getLong("elapsed_slices")));
    }

    /**
     * The thread-scoped categories flattened into two positional lists, read by SQL as one mapping
     * table — so the enum stays the single place an event type is tied to a category.
     */
    private record ThreadCategoryMapping(List<String> eventTypes, List<String> categories) {

        static ThreadCategoryMapping build() {
            List<String> eventTypes = new ArrayList<>();
            List<String> categories = new ArrayList<>();
            for (TraceContextCategory category : TraceContextCategory.values()) {
                if (category.scope() != TraceContextCategory.Scope.THREAD) {
                    continue;
                }
                for (String eventType : category.eventTypes()) {
                    // Promoted into a synthesized leaf span by the derivation, so already a bar
                    // under its span -- counting it here as a wait would say the same blocked
                    // microsecond twice. The manager rebuilds these categories' totals from the
                    // synthesized spans instead.
                    if (BlockingLeafSpans.EVENT_TYPES.contains(eventType)) {
                        continue;
                    }
                    eventTypes.add(eventType);
                    categories.add(category.name());
                }
            }
            return new ThreadCategoryMapping(List.copyOf(eventTypes), List.copyOf(categories));
        }
    }

    @Override
    public List<TraceSpanContextRecord> spanContext(long traceId) {
        ThreadCategoryMapping mapping = ThreadCategoryMapping.build();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("trace_id", traceId)
                .addValue("context_event_types", mapping.eventTypes())
                .addValue("context_categories", mapping.categories());

        return databaseClient.query(
                StatementLabel.TRACE_SPAN_CONTEXT,
                SPAN_CONTEXT,
                params,
                (rs, _) -> new TraceSpanContextRecord(
                        rs.getLong("span_id"),
                        TraceContextCategory.valueOf(rs.getString("category")),
                        rs.getLong("total_ns"),
                        rs.getLong("occurrences")));
    }

    @Override
    public List<TraceTimelineBucketRecord> timeline(int buckets) {
        return databaseClient.query(
                StatementLabel.TRACE_TIMELINE,
                TIMELINE,
                new MapSqlParameterSource().addValue("buckets", buckets),
                timelineMapper());
    }

    @Override
    public List<TraceTimelineBucketRecord> timelineOfOperation(TraceOperationId operation, int buckets) {
        return databaseClient.query(
                StatementLabel.TRACE_OPERATION_TIMELINE,
                TIMELINE_OF_OPERATION,
                operationParams(operation).addValue("buckets", buckets),
                timelineMapper());
    }

    /** One projection for both timelines — they differ in what they count, never in what they say. */
    private static RowMapper<TraceTimelineBucketRecord> timelineMapper() {
        return (rs, _) -> new TraceTimelineBucketRecord(
                rs.getLong("bucket_from_ms"),
                rs.getLong("count"),
                rs.getLong("error_count"),
                rs.getLong("max_ns"));
    }

    @Override
    public List<TraceOperationSpanRecord> spanBreakdownOfOperation(TraceOperationId operation, int limit) {
        MapSqlParameterSource params = operationParams(operation).addValue("limit", limit);

        return databaseClient.query(
                StatementLabel.TRACE_OPERATION_SPAN_BREAKDOWN,
                SPAN_BREAKDOWN_OF_OPERATION,
                params,
                (rs, _) -> new TraceOperationSpanRecord(
                        rs.getString("name"),
                        rs.getString("event_type"),
                        rs.getLong("occurrences"),
                        rs.getLong("trace_count"),
                        rs.getLong("total_ns"),
                        rs.getLong("self_ns"),
                        rs.getLong("p50_ns"),
                        rs.getLong("p50_self_ns"),
                        rs.getLong("p99_ns"),
                        rs.getLong("p99_self_ns"),
                        rs.getLong("max_ns")));
    }

    @Override
    public TraceOperationThreadsRecord threadsOfOperation(TraceOperationId operation) {
        return databaseClient.querySingle(
                        StatementLabel.TRACE_OPERATION_THREADS,
                        THREADS_OF_OPERATION,
                        operationParams(operation),
                        (rs, _) -> new TraceOperationThreadsRecord(
                                rs.getLong("distinct_threads"),
                                rs.getLong("platform_spans"),
                                rs.getLong("virtual_spans"),
                                rs.getLong("unknown_spans")))
                .orElse(TraceOperationThreadsRecord.EMPTY);
    }

    @Override
    public ThreadWindowEventsPage eventsInSpan(long threadHash, long fromEpochMillis, long toEpochMillis) {
        // One row past the cap: an extra row means the window held more than the page carries, and
        // saying so is the difference between a truncated list and a list passed off as complete.
        List<ThreadWindowEventRecord> rows = databaseClient.query(
                StatementLabel.TRACE_SPAN_EVENTS,
                EVENTS_IN_SPAN,
                ThreadWindowEvents.params(
                        threadHash, fromEpochMillis, toEpochMillis, ThreadWindowEvents.ROW_LIMIT + 1),
                ThreadWindowEvents.mapper());

        if (rows.size() <= ThreadWindowEvents.ROW_LIMIT) {
            return new ThreadWindowEventsPage(rows, false);
        }
        return new ThreadWindowEventsPage(List.copyOf(rows.subList(0, ThreadWindowEvents.ROW_LIMIT)), true);
    }

    @Override
    public List<EventFieldRecord> eventFieldsOf(List<String> eventTypes) {
        // An empty IN list is a SQL syntax error in DuckDB, and there is nothing to describe anyway.
        if (eventTypes.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("event_types", eventTypes);

        return databaseClient.query(
                StatementLabel.TRACE_EVENT_FIELDS,
                EVENT_FIELDS,
                params,
                (rs, _) -> new EventFieldRecord(
                        rs.getString("event_type"),
                        rs.getString("field"),
                        rs.getString("label"),
                        rs.getString("description"),
                        rs.getString("content_type")));
    }

}
