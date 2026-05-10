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
    parsed_at_ms      BIGINT  NOT NULL
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
