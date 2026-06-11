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

package cafe.jeffrey.shared.common;

/**
 * Strategy for writing parsed JFR events into the per-profile {@code events} table.
 * Configured via {@code jeffrey.microscope.profile.ingestion.event-writer}.
 */
public enum EventWriterMode {

    /**
     * Columnar bulk ingestion (~3x faster than the appender on large recordings).
     * Batches are accumulated into Arrow vectors, registered on the DuckDB connection
     * via the Arrow C Data Interface and inserted with a single bulk INSERT ... SELECT.
     * Falls back to {@link #APPENDER} automatically when the Arrow native library
     * cannot be initialized on the current platform.
     */
    ARROW,

    /**
     * Row-by-row ingestion through {@code DuckDBAppender}.
     * This is the original implementation; every appended value is a JNI crossing.
     */
    APPENDER
}
