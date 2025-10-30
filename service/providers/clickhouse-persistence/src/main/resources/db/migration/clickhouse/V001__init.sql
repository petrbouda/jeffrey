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

-- Frame dictionary for maximum deduplication and fast lookups
CREATE TABLE IF NOT EXISTS frames
(
    frame_hash       UInt64,
    class_name       LowCardinality(String),
    method_name      LowCardinality(String),
    frame_type       LowCardinality(String),
    line_number      UInt32,
    bytecode_index   UInt32
) ENGINE = ReplacingMergeTree()
ORDER BY frame_hash
SETTINGS index_granularity = 8192;

-- Stacktrace compositions using frame references for deduplication
CREATE TABLE IF NOT EXISTS stacktraces
(
    profile_id    String,
    stack_hash    UInt64,              -- Hash of frame_hashes array for deduplication
    frame_hashes  Array(UInt64),       -- References to frames table
    tag_ids       Array(UInt32)        -- Tags for categorization and filtering
) ENGINE = ReplacingMergeTree()
ORDER BY (profile_id, stack_hash)
SETTINGS index_granularity = 8192;

-- Thread information table
CREATE TABLE IF NOT EXISTS threads
(
    profile_id String,
    thread_id  UInt32,
    name       String,
    os_id      Nullable(UInt32),       -- Virtual threads don't have os_id
    java_id    Nullable(UInt32),
    is_virtual Bool
) ENGINE = ReplacingMergeTree()
ORDER BY (profile_id, thread_id)
SETTINGS index_granularity = 8192;

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
    columns        Nullable(String)
) ENGINE = ReplacingMergeTree()
ORDER BY (profile_id, name)
SETTINGS index_granularity = 8192;

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
    stack_hash                     Nullable(UInt64),
    thread_id                      Nullable(UInt32),
    fields                         String              -- JSON fields for event-specific data
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(start_timestamp)
ORDER BY (profile_id, event_type, start_timestamp_from_beginning, stack_hash)
SETTINGS index_granularity = 8192;

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
