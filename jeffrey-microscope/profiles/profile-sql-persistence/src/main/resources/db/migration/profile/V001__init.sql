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

--
-- PER-PROFILE DATABASE SCHEMA
-- Each profile has its own isolated database file.
-- Profile context (workspace, project) is stored in profile_info table.
--

--
-- CACHE TABLE
--
CREATE TABLE IF NOT EXISTS cache
(
    key     VARCHAR NOT NULL PRIMARY KEY,
    content BLOB    NOT NULL
);

--
-- EVENT TYPES TABLE
--
CREATE TABLE IF NOT EXISTS event_types
(
    name            VARCHAR NOT NULL PRIMARY KEY,
    label           VARCHAR NOT NULL,
    type_id         BIGINT,
    description     VARCHAR,
    categories      VARCHAR,
    source          VARCHAR NOT NULL,
    subtype         VARCHAR,
    has_stacktrace  BOOLEAN NOT NULL,
    extras          VARCHAR,
    settings        VARCHAR,
    columns         VARCHAR
);

--
-- FRAMES TABLE
--
CREATE TABLE IF NOT EXISTS frames
(
    frame_hash      BIGINT NOT NULL PRIMARY KEY,
    -- Class name with any hidden-class address stripped off, so it is the same string in every
    -- recording of the same application.
    class_name      VARCHAR,
    method_name     VARCHAR,
    frame_type      VARCHAR,  -- JIT/Interpreted/Native/C++
    line_number     INTEGER,
    bytecode_index  INTEGER,
    -- A hidden class (JEP 371) has no entry in any class loader's dictionary, so the JVM makes its
    -- name unique by appending its own address: `FilterChainProxy$$Lambda.0x0000000011cb1be8`. The
    -- address is redrawn on every run, which makes the full name useless as an identity when two
    -- recordings are compared -- 85% of the stacktraces in a Spring application contain at least
    -- one such frame. The stable part stays in class_name and the address lands here, so
    -- `hidden_class_id IS NOT NULL` is the whole is-this-hidden test: a null check, not a LIKE.
    hidden_class_id VARCHAR
);

--
-- STACKTRACES TABLE
--
CREATE TABLE IF NOT EXISTS stacktraces
(
    stacktrace_hash   BIGINT NOT NULL PRIMARY KEY,  -- Hash of frame_hashes array for deduplication
    type_id           INTEGER NOT NULL,              -- Numerical representation of the stacktrace type
    frame_hashes      BIGINT[],                      -- Array of references to frames table
    tag_ids           INTEGER[]                      -- Array of tags for categorization and filtering
);

--
-- EVENTS TABLE
-- The physical table is `events_raw`; every read goes through the `events` view below, which is
-- what keeps large pooled field values out of the stored rows without any reader knowing.
--
CREATE TABLE IF NOT EXISTS events_raw
(
    event_type      VARCHAR NOT NULL,
    start_timestamp TIMESTAMPTZ NOT NULL,
    -- Milliseconds elapsed since the profiling start (the recording's chunk start time).
    -- Matches the zero point of Java's RelativeTimeRange, so relative time-range filters
    -- and bucketing can run directly on this integer column (sargable, no per-row EPOCH_MS).
    start_timestamp_from_beginning BIGINT,
    duration        BIGINT,
    samples         BIGINT NOT NULL,
    weight          BIGINT,
    weight_entity   VARCHAR,
    stacktrace_hash BIGINT,    -- Reference to stacktraces.stacktrace_hash
    thread_hash     BIGINT,    -- Hash value
    fields          JSON,      -- JSON fields for event-specific data, minus any pooled value below
    -- The one field the parser pooled out of `fields`, and the reference to its text in
    -- field_texts -- both NULL when nothing qualified. A recording holds the same large text (a
    -- statement's SQL, a written file's path) on hundreds of thousands of events; the JFR constant
    -- pool deduplicates it on disk, a row store does not. The parser knows no field names: it
    -- lifts the largest string value over a size threshold, whichever key carries it, and the
    -- `events` view splices it back -- so nothing downstream knows the pooling exists. One field
    -- per event: in every recording observed, one field carries an order of magnitude more than
    -- the rest, and one (key, reference) pair keeps this table and the view trivial.
    pooled_field     VARCHAR,
    pooled_text_hash BIGINT
);

-- No ART indexes on events_raw: analytical scans don't use them, they slow down ingest and bloat
-- the database file. Instead, the table is re-clustered after parsing (CTAS ordered by event_type,
-- start_timestamp_from_beginning) so zone maps prune scans by event type and time range.

--
-- FIELD TEXTS TABLE
-- The pooled field values, one row per distinct text, keyed by the text's own 64-bit hash -- the
-- same convention stacktraces and threads use, so parallel parser threads agree on the id without
-- coordination. A hash collision fails the primary key loudly instead of silently attaching the
-- wrong text to an event.
--
CREATE TABLE IF NOT EXISTS field_texts
(
    text_hash BIGINT  NOT NULL PRIMARY KEY,
    text      VARCHAR NOT NULL
);

--
-- EVENTS VIEW
-- What every reader queries as `events`. Splices each pooled field value back into `fields` under
-- the key it was lifted from, so the JSON a reader extracts is what the recording declared; rows
-- with nothing pooled pass through untouched. Reads that never project `fields` (flamegraphs,
-- timeseries) pay only a LEFT JOIN against a table of a few dozen rows.
--
CREATE VIEW IF NOT EXISTS events AS
SELECT
    e.event_type,
    e.start_timestamp,
    e.start_timestamp_from_beginning,
    e.duration,
    e.samples,
    e.weight,
    e.weight_entity,
    e.stacktrace_hash,
    e.thread_hash,
    CASE WHEN e.pooled_text_hash IS NULL
         THEN e.fields
         ELSE json_merge_patch(e.fields, json_object(e.pooled_field, t.text)) END AS fields
FROM events_raw e
LEFT JOIN field_texts t ON t.text_hash = e.pooled_text_hash;

--
-- THREADS TABLE
--
CREATE TABLE IF NOT EXISTS threads
(
    thread_hash BIGINT   NOT NULL PRIMARY KEY,
    name        VARCHAR  NOT NULL,
    -- virtual threads do not have os_id
    os_id       BIGINT,
    java_id     BIGINT,
    is_virtual  BOOLEAN  NOT NULL
);

--
-- PROFILE INFO TABLE
-- Stores profile context (workspace, project) - always exactly one row per profile database.
-- Populated when the profile database is created.
--
CREATE TABLE IF NOT EXISTS profile_info
(
    profile_id   VARCHAR NOT NULL PRIMARY KEY,
    project_id   VARCHAR,
    workspace_id VARCHAR
);

--
-- PIPELINE RUNS TABLE
-- The terminal snapshot of one staged background run — heap-dump initialization, or any future
-- pipeline. Live progress is deliberately NOT here: it lives in memory and
-- dies with the process, because so does the work it describes. What a user needs after the fact is the
-- last completed run, which is what this stores, and it is what re-renders the kept timeline on return.
--
-- `stages` is a JSON array rather than a table of its own because it is written and read as a whole and
-- rendered as a whole; a row per stage would buy queries nobody asks and a join everybody pays for.
-- `scope_id` is '' rather than NULL for pipelines that run once per profile (the heap dump), and a
-- pipeline that runs once per something narrower stores that something — so the primary key works
-- without NULL-comparison rules and a batch is simply every row of its pipeline.
--
CREATE TABLE IF NOT EXISTS pipeline_runs
(
    pipeline_id      VARCHAR     NOT NULL,
    scope_id         VARCHAR     NOT NULL DEFAULT '',
    state            VARCHAR     NOT NULL,
    total_elapsed_ms BIGINT      NOT NULL,
    total_steps      INTEGER     NOT NULL,
    completed_steps  INTEGER     NOT NULL,
    error_code       VARCHAR,
    error_message    VARCHAR,
    stages           VARCHAR     NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL,
    completed_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (pipeline_id, scope_id)
);

--
-- TRACE SPANS TABLE
-- Spans arrive in `events` like any other JFR event, with their identity in the JSON `fields`.
-- Deriving them into typed columns once, after parsing, keeps every trace query off JSON
-- extraction: assembling a tree, listing the slowest traces and aggregating per operation all
-- run against BIGINTs instead of re-parsing `fields` per row, per query.
--
-- Every traced event type feeds this one table, which is what makes an HTTP request show its JDBC
-- statements as native children: they are rows here just like a hand-written span is. The events
-- record the whole span shape themselves -- name, kind, status and their own span id -- so the
-- derivation copies rather than interprets, and no event type is named anywhere in it.
--
-- `parent_span_id` is NULL for a root span (the wire encoding uses 0 for "absent", normalised here
-- so SQL null-semantics apply).
--
-- The primary key states the invariant every read relies on: a span id identifies exactly one span
-- of its trace. The derivation dedupes before inserting, so the key turns a future dedupe
-- regression into a loud failure instead of a silent skew between span_count and the waterfall.
--
CREATE TABLE IF NOT EXISTS trace_spans
(
    trace_id                       BIGINT      NOT NULL,
    span_id                        BIGINT      NOT NULL,
    parent_span_id                 BIGINT,
    name                           VARCHAR     NOT NULL,
    kind                           VARCHAR,
    status                         VARCHAR,
    error_type                     VARCHAR,
    start_timestamp                TIMESTAMPTZ NOT NULL,
    -- Same zero point as events.start_timestamp_from_beginning, so a trace can be lined up
    -- against the recording's other views without converting.
    start_timestamp_from_beginning BIGINT      NOT NULL,
    duration                       BIGINT      NOT NULL,
    -- The span's own time: its duration minus the stretches its same-thread children covered,
    -- merged so concurrent children are not subtracted twice and clipped to this span's own window.
    -- Stored rather than computed per read because both readers need it and they must agree: the
    -- waterfall asks per trace, where the interval merge is a walk over one tree, and the operation
    -- breakdown asks across every trace of a type, where it is not a group-by at all. Deriving it
    -- once leaves one definition of "self" for both.
    self_duration                  BIGINT      NOT NULL,
    thread_hash                    BIGINT,
    -- Which event produced this span: jeffrey.TraceSpan, jeffrey.HttpServerExchange, ...
    event_type                     VARCHAR     NOT NULL,
    -- What the span attached to itself: AbstractTracedEvent.attributes, an open JSON map whose keys
    -- are whatever the developer passed. Any traced event can carry one; in practice a hand-written
    -- span is what usually does.
    attributes                     VARCHAR,
    -- What the event declared beyond the span shape -- a statement's sql, params and rows, an
    -- exchange's uri, method and status code -- as a reference into trace_span_payloads. These are
    -- schema, not attributes (each is a labelled field of its event type), so they are kept apart
    -- from the map above. Stored as a reference because a million statement spans carry a few
    -- thousand distinct payloads: the text lives once in the payload table and the waterfall joins
    -- it back. Null for an event that declares nothing of its own, a hand-written span being the
    -- usual case.
    event_fields_ref               BIGINT,
    -- TRUE for a span the derivation synthesized out of a blocking JDK event (jdk.SocketRead,
    -- jdk.JavaMonitorEnter, ...) rather than read out of an instrumented span event. A synthesized
    -- span carries a minted id and is always a leaf under the innermost span open on its thread --
    -- the flag is what lets the UI style and filter promoted waits apart from recorded spans.
    synthesized                    BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Why a promoted I/O span's operation happened, when the derivation could tell from its stack:
    -- 'CLASS_LOADING' for a read the class loader asked for, NULL for everything else, including
    -- every recorded span and every promoted event whose recording captured no stack.
    --
    -- Set from the stack rather than from the path, because the path cannot answer it: a library
    -- unpacking its own native .so reads the same .jar the class loader does, through the same
    -- java.util.zip frames, and only a classloader frame further down the stack tells them apart.
    -- NULL therefore means "not known to be class loading" and never "known not to be".
    --
    -- Nullable and a string rather than a boolean so a second origin can be added without a
    -- migration and without re-deriving what this one means.
    io_origin                      VARCHAR,
    PRIMARY KEY (trace_id, span_id)
);

--
-- TRACES TABLE
-- One row per trace, so the trace list is a single scan of a small table rather than an
-- aggregation over every span. Derived from trace_spans immediately after it is filled.
--
-- The root is the earliest span without a parent; a trace whose real root went unrecorded (below
-- the event threshold, say) still gets one, because the ordering falls back to the earliest span.
-- Duration spans the whole trace rather than the root's own duration, which is the same number
-- whenever the root encloses its children and the more honest one when it does not.
--
CREATE TABLE IF NOT EXISTS traces
(
    trace_id                       BIGINT      NOT NULL PRIMARY KEY,
    -- The three together are the trace's *type*, which is what the Traces by Operation view groups on.
    -- Not the name alone: an inbound `GET /a` and an outbound call to the same path share a name and
    -- are not the same operation. All three are NOT NULL because the derivation COALESCEs each one
    -- from a NOT NULL source, and a nullable grouping key would make `<>` comparisons against it
    -- silently drop every row.
    root_name                      VARCHAR     NOT NULL,
    root_kind                      VARCHAR     NOT NULL,
    -- Which instrumentation opened the trace: jeffrey.HttpServerExchange for an inbound request,
    -- jeffrey.TraceSpan for a hand-written one, and so on. The name alone does not say -- a
    -- hand-written span can be named like a request -- and it is what tells a reader whether an
    -- operation came from a framework filter or from a Tracer call in their own code.
    root_event_type                VARCHAR     NOT NULL,
    -- Which span the three above were taken from. Lets a query exclude the trace's own root by
    -- identity rather than by name -- an operation that calls itself has nested spans named exactly
    -- like its root, and excluding by name dropped every one of them.
    root_span_id                   BIGINT      NOT NULL,
    start_timestamp                TIMESTAMPTZ NOT NULL,
    start_timestamp_from_beginning BIGINT      NOT NULL,
    duration                       BIGINT      NOT NULL,
    span_count                     INTEGER     NOT NULL,
    error_count                    INTEGER     NOT NULL,
    -- Whether any span of this trace ran on a platform thread, and therefore whether a flamegraph
    -- can be drawn for it at all: the profiler attributes samples to the carrier, never to the
    -- virtual thread. Stored rather than recomputed per query -- it is a pure function of the
    -- trace's spans, and as a correlated EXISTS it was evaluated for every candidate trace before
    -- the LIMIT. A span whose thread did not resolve counts as *not* platform: an unresolved thread
    -- cannot promise samples, and claiming otherwise offers a flamegraph that comes back empty.
    has_platform_span              BOOLEAN     NOT NULL
);

--
-- TRACE NOTIFICATIONS TABLE
-- What the application said while a trace was running: jeffrey.Notification events, derived into
-- typed columns for the same reason spans are -- the waterfall reads them per trace, and it should
-- not re-parse JSON to do it.
--
-- A notification is an instant, not a span. It stamps the enclosing span's ids onto itself at
-- commit time, so the attribution here is a copy rather than an inference: unlike a throw, a
-- notification can be raised on a pool thread or a callback for work that belongs elsewhere, and
-- thread plus window would file it against whatever happened to be running.
--
-- `span_id` is NULL when the notification carried a trace but no span, and also when the span it
-- named was never recorded (below a JFR threshold, or from a chunk this profile does not hold).
-- Both mean the same thing to a reader: it belongs to the trace, but there is no bar to draw it on.
-- Nulling the second case rather than keeping a dangling id is what makes `span_id IS NOT NULL`
-- mean "this can be drawn against a span".
--
-- `notification_id` is the source event's rowid, which is unique within the recording and stable
-- for the life of a derivation. Two notifications of the same type, on the same span, in the same
-- microsecond are still two rows.
--
CREATE TABLE IF NOT EXISTS trace_notifications
(
    trace_id                       BIGINT      NOT NULL,
    span_id                        BIGINT,
    notification_id                BIGINT      NOT NULL,
    start_timestamp                TIMESTAMPTZ NOT NULL,
    -- Same zero point as events.start_timestamp_from_beginning and trace_spans, so a notification
    -- lines up against the bars without converting.
    start_timestamp_from_beginning BIGINT      NOT NULL,
    type                           VARCHAR,
    -- Reference into trace_notification_messages. The text lives once per distinct message rather
    -- than once per notification: a message is a property of the *kind* of notification, so a run
    -- that raises the same kind ten thousand times repeats one sentence ten thousand times.
    message_ref                    BIGINT,
    severity                       VARCHAR,
    category                       VARCHAR,
    source                         VARCHAR,
    -- What the notification attached to itself: AbstractTracedInstant.attributes, the same open JSON
    -- map a span carries in trace_spans.attributes, built by the same EventAttributes builder. Kept
    -- as the raw text the recording held, inline and undeduplicated, for the same reason the span
    -- column is: the detail read hands it to the UI verbatim, and the searchable form lives in
    -- trace_notification_attributes.
    attributes                     VARCHAR,
    thread_hash                    BIGINT,
    PRIMARY KEY (trace_id, notification_id)
);

--
-- TRACE NOTIFICATION MESSAGES
-- The distinct notification message texts, one row per distinct text, keyed by the text's own 64-bit
-- hash -- the same convention trace_span_payloads and trace_attribute_values use, so the derivation
-- computes each reference inline.
--
-- Worth a table of its own because a notification's message is the one field guaranteed to repeat: it
-- says what *kind* of thing happened, never which one, so every occurrence of a kind carries a
-- byte-identical sentence. In practice this table holds one row per notification type -- a couple of
-- dozen -- however many notifications the recording contains.
--
-- Keyed by hash rather than by `type` on purpose. That the message follows from the type is a rule
-- Jeffrey's own emitter enforces, not something the recording guarantees: a third-party application
-- writing jeffrey.Notification events may vary the message for one type, and hashing the text
-- degrades to more rows rather than silently attaching the wrong sentence to an event.
--
CREATE TABLE IF NOT EXISTS trace_notification_messages
(
    message_id   BIGINT  NOT NULL PRIMARY KEY,
    message_text VARCHAR NOT NULL
);

--
-- TRACE EXCEPTIONS TABLE
-- The throws that happened inside a trace: jdk.JavaExceptionThrow and jdk.JavaErrorThrow, which the
-- parser already stores and the Exceptions view already reads. Nothing new is ingested here; what
-- is derived is the correlation to a span.
--
-- Attribution is by thread and window, and that is sound here in a way it is not for a
-- notification: a throw is always recorded on the thread that threw it, at the instant it threw.
-- The span chosen is the innermost one containing that instant on that thread -- the narrowest
-- window wins -- which is the same "innermost open span" rule the promoted blocking spans use.
--
-- `escaped` is TRUE when the thrown class matches the attributed span's own `error_type`: this
-- throw is the reason that span failed. It is what turns a bare class name in trace_spans into a
-- class name with a message, an instant and a stack behind it. A throw that was caught inside the
-- span leaves it FALSE, which is most of them -- exceptions are cheap to make and services throw
-- them for control flow.
--
-- Kept apart from trace_notifications rather than sharing one table with a `kind` column: the two
-- agree on when and where and on almost nothing else, so one table would be half NULLs and every
-- read would have to know which half applied.
--
CREATE TABLE IF NOT EXISTS trace_exceptions
(
    trace_id                       BIGINT      NOT NULL,
    span_id                        BIGINT      NOT NULL,
    exception_id                   BIGINT      NOT NULL,
    start_timestamp                TIMESTAMPTZ NOT NULL,
    start_timestamp_from_beginning BIGINT      NOT NULL,
    -- The event type it came from, so an Error can be told from an Exception without a class-name
    -- heuristic.
    event_type                     VARCHAR     NOT NULL,
    thrown_class                   VARCHAR     NOT NULL,
    message                        VARCHAR,
    escaped                        BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Reference into stacktraces, so the drill-down can open the throw's stack. NULL when the
    -- recording captured no stack for it.
    stacktrace_hash                BIGINT,
    thread_hash                    BIGINT,
    PRIMARY KEY (trace_id, exception_id)
);

--
-- TRACE SPAN PAYLOADS TABLE
-- The distinct `event_fields` payloads, one row per distinct JSON text, keyed by the text's own
-- 64-bit hash (DuckDB's hash(), cast to BIGINT) so the derivation can compute a span's reference
-- inline without coordinating a sequence. A collision fails the primary key loudly during
-- derivation instead of silently showing one span another span's payload.
--
CREATE TABLE IF NOT EXISTS trace_span_payloads
(
    payload_id BIGINT  NOT NULL PRIMARY KEY,
    payload    VARCHAR NOT NULL
);

-- No secondary index on trace_spans: the derivation inserts it ordered by (trace_id,
-- start_timestamp), so row-group zone maps prune a single-trace read as effectively as the ART
-- index this table used to carry -- without the index's build time during derivation or its
-- footprint in the file. The composite primary key stays: it states the one-span-per-id invariant
-- the waterfall relies on.
CREATE INDEX IF NOT EXISTS traces_operation_idx ON traces (root_name, root_kind, root_event_type);

--
-- TRACE SPAN ATTRIBUTES
-- The key/value payload of every span, flattened to one row per (span, key), so an attribute can be
-- filtered, grouped and ranked in SQL instead of being re-parsed out of JSON on every read.
--
-- Three sources feed it, kept apart because they mean different things to a reader:
--   ATTRIBUTE   -- the open map from AbstractTracedEvent.attributes, whatever the developer passed
--   EVENT_FIELD -- what the event type declares about itself; `owner` is that event type, since
--                  `rows` on a JdbcQuery and `rows` on some other event are not the same key
--   SPAN_SHAPE  -- the columns every span already has (name, kind, status, ...), exposed here so
--                  one query surface answers "spans that failed" and "spans of this tenant" alike
--
-- A trace can carry two different values of the same key on different spans; that is a property of
-- the data, not a defect, and every read that groups by value says so rather than picking one.
--
-- value_num is filled only where the text casts to a number. It is what makes `rows > 10000` and
-- numeric bucketing possible without parsing the value again per row, and it is NULL for every
-- value that is not a number -- including `true`, which casts to nothing on purpose so a boolean
-- never sorts between two integers.
--
CREATE TABLE IF NOT EXISTS trace_span_attributes
(
    trace_id   BIGINT  NOT NULL,
    span_id    BIGINT  NOT NULL,
    source     VARCHAR NOT NULL,
    -- The event type an EVENT_FIELD belongs to; NULL for the other two sources, whose keys are
    -- global. Nullable rather than a sentinel because it is compared with IS NULL, never with `=`.
    owner      VARCHAR,
    attr_key   VARCHAR NOT NULL,
    -- Reference into trace_attribute_values. The text lives once per distinct value rather than
    -- once per row: a million statement spans carry a few dozen distinct SQL texts, and storing
    -- each text inline multiplied this table's footprint by the repetition. Every read that shows
    -- or compares text joins the value table; equality and ordering on the reference alone are
    -- meaningless and no query performs them.
    value_id   BIGINT  NOT NULL,
    value_num  DOUBLE,
    -- Which event type the span carrying this value was, copied from trace_spans at derivation.
    -- Distinct from `owner`: owner says which event type *declares* a key, and only EVENT_FIELD has
    -- one, while this says which event type a value was *recorded on*, which every row has. It is
    -- what lets `tenant` -- a key no event type declares -- be listed under the types that carry it.
    event_type VARCHAR NOT NULL
);

--
-- TRACE NOTIFICATION ATTRIBUTES
-- The same thing trace_span_attributes is, for the other half of what a trace is made of: one row
-- per (notification, key), so a notification can be filtered in SQL rather than re-parsed out of
-- JSON on every read.
--
-- A separate table rather than more rows in trace_span_attributes, and not because of tidiness:
--   * that table's span_id is NOT NULL, and a notification's span legitimately is not -- a sentinel
--     would collide with the "absent" encoding the whole trace layer uses, and MATCH_HITS hands
--     span_id straight to the UI, which would print a span id that never existed;
--   * TraceAttributeScope.SPAN groups by (trace_id, span_id), so every notification in a trace would
--     collapse into one pseudo-span and the scope would silently answer the wrong question;
--   * a notification's attributes are not a span's. A search for `status = ERROR` over SPAN_SHAPE
--     must not match something that merely said so.
-- trace_exceptions is kept apart from trace_notifications for the same kind of reason, spelled out
-- there: one table would be half NULLs and every read would have to know which half applied.
--
-- Two sources feed it, mirroring the span table's three:
--   NOTIFICATION_ATTRIBUTE -- the open map from AbstractTracedInstant.attributes
--   NOTIFICATION_SHAPE     -- the columns every notification already has (type, message,
--                             severity, category, source)
--
-- Note the collision a reader will meet here, because it looks like a bug and is not: a notification
-- *field* is called `source`, and this table's discriminator *column* is also called `source`. So a
-- shape row for that field reads `source = 'NOTIFICATION_SHAPE' AND attr_key = 'source'`. Two
-- different things; the key is spelled the way the event spells it, on purpose, so that what the
-- search offers matches what the detail panel showed.
--
-- span_id is copied from trace_notifications rather than joined at read time, so a search hit can
-- name the bar it fired on -- and honestly say there is none -- without a second join. value_id and
-- the value dictionary are shared with the span index: one text recorded by a span and by a
-- notification is still one row there.
--
CREATE TABLE IF NOT EXISTS trace_notification_attributes
(
    trace_id        BIGINT  NOT NULL,
    notification_id BIGINT  NOT NULL,
    -- NULL when there is no bar to draw it against, exactly as in trace_notifications.
    span_id         BIGINT,
    source          VARCHAR NOT NULL,
    -- Always NULL today: a notification declares no owned fields, so it has no EVENT_FIELD analogue.
    -- Kept so the catalog reads both index tables with one query shape.
    owner           VARCHAR,
    attr_key        VARCHAR NOT NULL,
    -- Reference into trace_attribute_values, shared with trace_span_attributes.
    value_id        BIGINT  NOT NULL,
    value_num       DOUBLE,
    -- jeffrey.Notification today. Carried rather than assumed so the catalog, the key picker and the
    -- per-event-type counts join uniformly across both index tables.
    event_type      VARCHAR NOT NULL
);

-- No indexes, for the same reason trace_span_attributes carries none: the derivation inserts this
-- ordered by (trace_id, notification_id), so zone maps prune the search's per-page hit lookup by
-- trace id, and the facet reads are grouped scans over one key that no index served selectively.

--
-- TRACE ATTRIBUTE VALUES TABLE
-- The distinct attribute value texts, one row per distinct text across every source and key,
-- keyed by the text's own 64-bit hash (DuckDB's hash(), cast to BIGINT) so the derivation computes
-- each row's reference inline. Shared across keys deliberately: `UNSET` recorded by ten thousand
-- spans under three different keys is still one row here. A collision fails the primary key loudly
-- during derivation instead of silently merging two values.
--
CREATE TABLE IF NOT EXISTS trace_attribute_values
(
    value_id   BIGINT  NOT NULL PRIMARY KEY,
    value_text VARCHAR NOT NULL
);

--
-- TRACE ATTRIBUTE CATALOG
-- One row per key, written by the same derivation. Two jobs: it is what the key list renders
-- without touching the rows above, and `distinct_values` is the guard the whole feature leans on --
-- a key with eighteen thousand values (a user id, a SQL statement) is search-only, and must never
-- become a facet list, a heatmap axis or a candidate in the difference ranking.
--
-- Summarised from both index tables, so a notification key is listed here beside a span key.
--
CREATE TABLE IF NOT EXISTS trace_attribute_keys
(
    source          VARCHAR NOT NULL,
    owner           VARCHAR,
    attr_key        VARCHAR NOT NULL,
    -- STRING | NUMBER | BOOLEAN, inferred from the values themselves: what the UI offers as
    -- operators, and what decides whether a key's values are bucketed by magnitude when ranked.
    value_kind      VARCHAR NOT NULL,
    distinct_values BIGINT  NOT NULL,
    -- How many carriers of the key there are: spans for a span source, notifications for a
    -- notification one. Named for the carrier rather than for either, so neither reading is a lie.
    carrier_count   BIGINT  NOT NULL,
    trace_count     BIGINT  NOT NULL
);

--
-- WHICH KEYS EACH EVENT TYPE CARRIES
-- The catalog above answers "what keys does this profile have"; this answers "what keys do spans of
-- this event type have", which is what the two-step picker walks. jeffrey.Notification appears here
-- as an event type of its own, which is what makes its keys reachable from the picker at all.
--
-- A separate table rather than a column on the catalog, because a key is not per event type: one
-- `tenant` attached at a call site rides on an HTTP span and a Kafka span alike, and folding the
-- event type into the catalog's key would split it into two keys that are the same key. Here it is
-- a second row for the same key, with the counts scoped to that event type -- so the picker can say
-- how many values `tenant` took *on HTTP spans* without changing what `tenant` is.
--
CREATE TABLE IF NOT EXISTS trace_attribute_key_event_types
(
    event_type      VARCHAR NOT NULL,
    source          VARCHAR NOT NULL,
    owner           VARCHAR,
    attr_key        VARCHAR NOT NULL,
    distinct_values BIGINT  NOT NULL,
    -- Carriers scoped to this event type -- see the note on trace_attribute_keys.carrier_count.
    carrier_count   BIGINT  NOT NULL,
    trace_count     BIGINT  NOT NULL
);

-- No indexes on trace_span_attributes: the derivation inserts it ordered by (trace_id, span_id),
-- so zone maps prune the search's per-page hit lookup by trace id, and the facet reads -- grouped
-- scans over one key -- never used an index selectively anyway. The two ART indexes this table
-- used to carry cost more to build and store than every read they served; a full scan of the
-- reference-encoded table answers each of those reads in well under an interactive budget.
-- The picker's second step reads this by event type and nothing else.
CREATE INDEX IF NOT EXISTS trace_attribute_key_event_types_idx
    ON trace_attribute_key_event_types (event_type);
