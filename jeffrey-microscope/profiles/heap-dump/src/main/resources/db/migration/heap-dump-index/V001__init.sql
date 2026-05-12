--
-- Jeffrey
-- Copyright (C) 2026 Petr Bouda
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affero General Public License
-- along with this program.  If not, see <http://www.gnu.org/licenses/>.
--

--
-- HEAP DUMP INDEX SCHEMA
-- One database file per heap dump, sibling to the .hprof:
--   <heap-dump>/<name>.hprof.idx.duckdb
--
-- The schema is recreated whenever the index is rebuilt; never migrated.
-- Per project policy, modify this file in place rather than introducing V002.
--

--
-- DUMP_METADATA
-- Single-row table describing the parse run. Used to detect stale indexes
-- (mtime / size mismatch with the .hprof) and report parse health to the UI.
--
CREATE TABLE IF NOT EXISTS dump_metadata
(
    hprof_path        VARCHAR NOT NULL,
    hprof_size_bytes  BIGINT  NOT NULL,
    hprof_mtime_ms    BIGINT  NOT NULL,
    id_size           INTEGER NOT NULL,
    hprof_version     VARCHAR NOT NULL,
    timestamp_ms      BIGINT  NOT NULL,
    bytes_parsed      BIGINT  NOT NULL,
    record_count      BIGINT  NOT NULL,
    warning_count     BIGINT  NOT NULL,
    truncated         BOOLEAN NOT NULL,
    parser_version    VARCHAR NOT NULL,
    parsed_at_ms      BIGINT  NOT NULL,
    -- compressed_oops: pointer-compression mode the parser assumed when computing
    -- per-instance shallow_size. Inferred at index build time from id_size + the
    -- .hprof file size (proxy for total heap). Baked into shallow_size so every
    -- analyzer downstream gets the right header bytes per array.
    compressed_oops   BOOLEAN NOT NULL
);

--
-- STRING
-- HPROF UTF-8 string pool (from STRING records). Referenced by class names and
-- field names via string_id.
--
CREATE TABLE IF NOT EXISTS string
(
    string_id BIGINT  NOT NULL PRIMARY KEY,
    value     VARCHAR NOT NULL
);

--
-- CLASS
-- One row per loaded class (from LOAD_CLASS + CLASS_DUMP records).
--
CREATE TABLE IF NOT EXISTS class
(
    class_id              BIGINT  NOT NULL PRIMARY KEY,
    class_serial          INTEGER NOT NULL,
    name                  VARCHAR NOT NULL,
    is_array              BOOLEAN NOT NULL,
    super_class_id        BIGINT,
    classloader_id        BIGINT,
    signers_id            BIGINT,
    protection_domain_id  BIGINT,
    instance_size         INTEGER NOT NULL,
    static_fields_size    INTEGER NOT NULL,
    file_offset           BIGINT  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_class_name ON class(name);
CREATE INDEX IF NOT EXISTS idx_class_super ON class(super_class_id);
CREATE INDEX IF NOT EXISTS idx_class_is_array ON class(is_array);

--
-- INSTANCE
-- One row per object in the heap (from INSTANCE_DUMP, OBJECT_ARRAY_DUMP and
-- PRIMITIVE_ARRAY_DUMP sub-records). file_offset points back to the body so
-- the parser can lazily decode field values without storing them here.
--
-- record_kind: 0=instance, 1=object_array, 2=primitive_array
-- shallow_size: total bytes occupied (header + payload); MAT @usedHeapSize
-- array_length: nullable; only for arrays; MAT @length
-- primitive_type: nullable; only for primitive arrays
--
CREATE TABLE IF NOT EXISTS instance
(
    instance_id    BIGINT  NOT NULL PRIMARY KEY,
    class_id       BIGINT,
    file_offset    BIGINT  NOT NULL,
    record_kind    TINYINT NOT NULL,
    shallow_size   INTEGER NOT NULL,
    array_length   INTEGER,
    primitive_type TINYINT
);

CREATE INDEX IF NOT EXISTS idx_instance_class ON instance(class_id);

--
-- CLASS_INSTANCE_FIELD
-- Per-class instance field descriptors (one row per field, in declaration order).
-- field_index is the field's 0-based position within THIS class only — the
-- complete instance-field layout for an object is the concatenation of all its
-- ancestor classes' rows, walked most-derived-first per HPROF.
--
CREATE TABLE IF NOT EXISTS class_instance_field
(
    class_id    BIGINT  NOT NULL,
    field_index INTEGER NOT NULL,
    name        VARCHAR NOT NULL,
    basic_type  TINYINT NOT NULL,
    PRIMARY KEY (class_id, field_index)
);

--
-- GC_ROOT
-- One row per GC root reference (from ROOT_* sub-records).
-- root_kind maps directly to the HPROF sub-tag byte.
--
CREATE TABLE IF NOT EXISTS gc_root
(
    instance_id    BIGINT  NOT NULL,
    root_kind      TINYINT NOT NULL,
    thread_serial  INTEGER,
    frame_index    INTEGER,
    file_offset    BIGINT  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_gc_root_instance ON gc_root(instance_id);

--
-- OUTBOUND_REF
-- Object-to-object references emitted during the index build pass.
-- field_kind: 0=instance_field, 1=array_element, 2=class_static
-- field_id:   field index for instance/static, array index for arrays
--
-- Indexed on target_id for inbounds() / leak suspect queries.
--
CREATE TABLE IF NOT EXISTS outbound_ref
(
    source_id  BIGINT  NOT NULL,
    target_id  BIGINT  NOT NULL,
    field_kind TINYINT NOT NULL,
    field_id   INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_outbound_source ON outbound_ref(source_id);
CREATE INDEX IF NOT EXISTS idx_outbound_target ON outbound_ref(target_id);

--
-- DOMINATOR
-- One row per instance: its immediate dominator in the heap reference graph.
-- Built lazily by DominatorTreeBuilder; the table stays empty until requested.
-- Instances rooted directly at the (virtual) root have dominator_id = 0.
--
CREATE TABLE IF NOT EXISTS dominator
(
    instance_id   BIGINT NOT NULL PRIMARY KEY,
    dominator_id  BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dominator_parent ON dominator(dominator_id);

--
-- RETAINED_SIZE
-- One row per instance: total bytes that would be reclaimed if the instance
-- became unreachable. Computed bottom-up over the dominator tree; populated
-- by the same lazy build that fills the dominator table.
--
CREATE TABLE IF NOT EXISTS retained_size
(
    instance_id BIGINT NOT NULL PRIMARY KEY,
    bytes       BIGINT NOT NULL
);

--
-- STACK_FRAME
-- One row per HPROF STACK_FRAME top-level record. class_name is resolved at
-- index-build time from the LOAD_CLASS map: STACK_FRAME references classes
-- via class_serial, but the matching CLASS_DUMP may be absent (framework
-- classes loaded but never instantiated still appear on stacks). Storing
-- the resolved name avoids a join through the class table and survives
-- those missing CLASS_DUMP rows.
-- line_number stores the raw HPROF value: >=1 normal, -1 no info,
--   -2 compiled, -3 native.
--
CREATE TABLE IF NOT EXISTS stack_frame
(
    frame_id          BIGINT  NOT NULL PRIMARY KEY,
    class_name        VARCHAR NOT NULL,
    method_name       VARCHAR NOT NULL,
    method_signature  VARCHAR NOT NULL,
    source_file       VARCHAR,
    line_number       INTEGER NOT NULL
);

--
-- STACK_TRACE_FRAME
-- Ordered membership of frames in a stack trace. frame_index is 0-based
-- with the topmost (most-recent) frame at 0. thread_serial duplicates the
-- one on the parent STACK_TRACE so the per-thread frame query can avoid
-- a join when the analyzer already knows the thread.
--
CREATE TABLE IF NOT EXISTS stack_trace_frame
(
    trace_serial   INTEGER NOT NULL,
    thread_serial  INTEGER NOT NULL,
    frame_index    INTEGER NOT NULL,
    frame_id       BIGINT  NOT NULL,
    PRIMARY KEY (trace_serial, frame_index)
);

CREATE INDEX IF NOT EXISTS idx_stack_trace_frame_thread ON stack_trace_frame(thread_serial);

--
-- PARSE_WARNING
-- Forensic record of any record that was skipped, truncated, or recovered
-- during the parse. Surfaced to the UI via dump_metadata.warning_count.
-- severity: 0=info, 1=warn, 2=error
--
CREATE TABLE IF NOT EXISTS parse_warning
(
    file_offset BIGINT  NOT NULL,
    record_kind INTEGER,
    severity    TINYINT NOT NULL,
    message     VARCHAR NOT NULL
);
