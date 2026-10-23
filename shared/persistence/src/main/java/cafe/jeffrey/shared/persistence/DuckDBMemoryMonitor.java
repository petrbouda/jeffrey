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
package cafe.jeffrey.shared.persistence;

/**
 * Opts a DuckDB database into the JDBC driver's {@code duckdb.MemoryUsage} JFR event (driver 2.0):
 * one event per DuckDB memory tag per period, labelled with the component named here. DuckDB's
 * memory lives outside the Java heap, so no JDK event accounts for it -- and the heap-dump index
 * build and profile parsing are where Jeffrey's own footprint grows.
 * <p>
 * Free until asked for: the driver samples only while a recording enables the event, which
 * {@code run-microscope.sh} does every second.
 */
public final class DuckDBMemoryMonitor {

    /** The connection property whose value labels the database's events; absent means no events. */
    public static final String PROPERTY = "jdbc_jfr_memory_monitor";

    /** Microscope's catalogue database. */
    public static final String CORE_DATABASE = "core-database";

    /** A profile's database; the profile id follows. */
    public static final String PROFILE_DATABASE_PREFIX = "profile-database-";

    /** A heap dump's index database, while it is built and while it is read. */
    public static final String HEAP_DUMP_INDEX = "heap-dump-index";

    /** The in-memory databases the heap-dump parser stages its Parquet shards through. */
    public static final String HEAP_DUMP_STAGING = "heap-dump-staging";

    private DuckDBMemoryMonitor() {
    }
}
