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

-- ClickHouse Schema for Jeffrey JFR Profiling Data
-- Optimized for high-performance stacktrace aggregation and flamegraph generation

--
-- FRAME TABLES - NORMALIZED APPROACH
--

-- Frame dictionary for maximum deduplication and fast lookups
CREATE TABLE IF NOT EXISTS frames
(
    frame_hash       UInt64,
    class_name       LowCardinality(String),
    method_name      LowCardinality(String),
    compilation_type LowCardinality(String),    -- JIT/Interpreted/Native/C++
    line_number      UInt32,
    bytecode_index   UInt32,
    first_seen       DateTime64(9) DEFAULT now64(),
    last_seen        DateTime64(9) DEFAULT now64()
) ENGINE = ReplacingMergeTree(last_seen)
ORDER BY frame_hash
SETTINGS index_granularity = 8192;

-- Stacktrace compositions using frame references for deduplication
CREATE TABLE IF NOT EXISTS stacktraces
(
    profile_id    String,
    stacktrace_id UInt64,
    stack_hash    UInt64,              -- Hash of frame_hashes array for deduplication
    frame_hashes  Array(UInt64),       -- References to frames table
    depth         UInt16,
    created_at    DateTime64(9) DEFAULT now64()
) ENGINE = ReplacingMergeTree(created_at)
ORDER BY (profile_id, stacktrace_id)
SETTINGS index_granularity = 8192;

-- Stacktrace tags for categorization and filtering
CREATE TABLE IF NOT EXISTS stacktrace_tags
(
    profile_id    String,
    stacktrace_id UInt64,
    tag_id        UInt32
) ENGINE = MergeTree()
ORDER BY (profile_id, stacktrace_id, tag_id)
SETTINGS index_granularity = 8192;

--
-- THREAD TABLES
--

-- Thread information table
CREATE TABLE IF NOT EXISTS threads
(
    profile_id String,
    thread_id  UInt32,
    name       String,
    os_id      Nullable(UInt32),       -- Virtual threads don't have os_id
    java_id    Nullable(UInt32),
    is_virtual Bool,
    created_at DateTime64(9) DEFAULT now64()
) ENGINE = ReplacingMergeTree(created_at)
ORDER BY (profile_id, thread_id)
SETTINGS index_granularity = 8192;

--
-- EVENT TYPE METADATA
--

-- Event types configuration and metadata
CREATE TABLE IF NOT EXISTS event_types
(
    profile_id     String,
    name           LowCardinality(String),
    label          String,
    type_id        Nullable(UInt32),
    description    Nullable(String),
    categories     Nullable(String),
    source         LowCardinality(String),
    subtype        Nullable(LowCardinality(String)),
    samples        UInt64,
    weight         Nullable(UInt64),
    has_stacktrace Bool,
    calculated     Bool,
    extras         Nullable(String),
    settings       Nullable(String),
    columns        Nullable(String),
    created_at     DateTime64(9) DEFAULT now64()
) ENGINE = ReplacingMergeTree(created_at)
ORDER BY (profile_id, name)
SETTINGS index_granularity = 8192;

--
-- MAIN EVENTS TABLE - OPTIMIZED FOR TIME-SERIES QUERIES
--

-- Events table optimized for flamegraph queries and time-range filtering
CREATE TABLE IF NOT EXISTS events
(
    profile_id                     String,
    event_id                       UInt64,
    event_type                     LowCardinality(String),
    start_timestamp                DateTime64(9),
    start_timestamp_from_beginning UInt64,
    end_timestamp                  Nullable(DateTime64(9)),
    end_timestamp_from_beginning   Nullable(UInt64),
    duration                       Nullable(UInt64),
    samples                        UInt32,
    weight                         Nullable(UInt64),
    weight_entity                  LowCardinality(String),
    stacktrace_id                  Nullable(UInt64),
    thread_id                      Nullable(UInt32),
    fields                         String,              -- JSON fields for event-specific data
    ingestion_time                 DateTime64(9) DEFAULT now64()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(start_timestamp)
ORDER BY (profile_id, event_type, start_timestamp_from_beginning, stacktrace_id)
SETTINGS index_granularity = 8192;

--
-- NOTE: Profile and project metadata remain in SQLite
-- ClickHouse only stores the profiling event data
--

--
-- PERFORMANCE INDEXES
--

-- Frame search indexes for pattern matching
CREATE INDEX IF NOT EXISTS idx_frames_class_name ON frames (class_name) TYPE tokenbf_v1(10240, 3, 0);
CREATE INDEX IF NOT EXISTS idx_frames_method_name ON frames (method_name) TYPE tokenbf_v1(10240, 3, 0);

-- Event filtering indexes
CREATE INDEX IF NOT EXISTS idx_events_time_range ON events (profile_id, event_type, start_timestamp_from_beginning);
CREATE INDEX IF NOT EXISTS idx_events_weight_entity ON events (profile_id, event_type, weight_entity) TYPE bloom_filter(0.01);

-- Stacktrace lookup optimization
CREATE INDEX IF NOT EXISTS idx_stacktraces_profile ON stacktraces (profile_id) TYPE bloom_filter(0.01);

-- Thread lookup optimization
CREATE INDEX IF NOT EXISTS idx_threads_name ON threads (profile_id, name) TYPE tokenbf_v1(10240, 3, 0);

--
-- DICTIONARY FOR FAST FRAME LOOKUPS
--

-- Dictionary for ultra-fast frame resolution in queries
CREATE DICTIONARY IF NOT EXISTS frames_dict
(
    frame_hash UInt64,
    class_name String,
    method_name String,
    compilation_type String,
    line_number UInt32,
    bytecode_index UInt32
)
PRIMARY KEY frame_hash
SOURCE(CLICKHOUSE(HOST 'localhost' PORT 9000 USER 'default' PASSWORD '' DB 'jeffrey' TABLE 'frames'))
LAYOUT(HASHED())
LIFETIME(MIN 300 MAX 600);

--
-- MATERIALIZED VIEWS FOR COMMON QUERIES
--

-- Pre-aggregated stacktrace statistics for faster flamegraph generation
CREATE MATERIALIZED VIEW IF NOT EXISTS stacktrace_stats_mv
ENGINE = SummingMergeTree()
ORDER BY (profile_id, event_type, stacktrace_id)
AS SELECT
    profile_id,
    event_type,
    stacktrace_id,
    sum(samples) as total_samples,
    sum(weight) as total_weight,
    count() as event_count,
    any(weight_entity) as weight_entity
FROM events
WHERE stacktrace_id IS NOT NULL
GROUP BY profile_id, event_type, stacktrace_id;

-- Frame popularity statistics for performance analysis
CREATE MATERIALIZED VIEW IF NOT EXISTS frame_stats_mv
ENGINE = SummingMergeTree()
ORDER BY (profile_id, event_type, frame_hash)
AS SELECT
    e.profile_id,
    e.event_type,
    arrayJoin(st.frame_hashes) as frame_hash,
    sum(e.samples) as total_samples,
    sum(e.weight) as total_weight,
    count() as occurrence_count
FROM events e
LEFT JOIN stacktraces st ON (e.profile_id = st.profile_id AND e.stacktrace_id = st.stacktrace_id)
WHERE e.stacktrace_id IS NOT NULL
GROUP BY e.profile_id, e.event_type, frame_hash;