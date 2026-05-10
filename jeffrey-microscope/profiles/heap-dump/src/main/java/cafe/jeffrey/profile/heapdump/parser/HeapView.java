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
package cafe.jeffrey.profile.heapdump.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Read-side façade over a heap-dump index DB.
 *
 * Replaces the consumer-side surface that the rewritten analyzers will use in
 * place of NetBeans' {@code org.netbeans.lib.profiler.heap.Heap}. Methods that
 * return bounded result sets ({@link #classes()}, {@link #gcRoots()}, the
 * histogram) hand back lists; methods that may scan the full instance table
 * return {@link Stream} and must be closed by the caller via try-with-resources.
 *
 * Implementations are read-only: the underlying DuckDB connection is opened in
 * {@code access_mode=read_only} so multiple {@link HeapView} instances can run
 * concurrently against the same index.
 */
public interface HeapView extends AutoCloseable {

    /**
     * Opens a {@link HeapView} over the given index DB file. The file must
     * already have been built by {@link HprofIndex#build}; no schema is
     * applied here.
     */
    static HeapView open(Path indexDbPath) throws SQLException, IOException {
        return DuckDbHeapView.open(indexDbPath);
    }

    DumpMetadata metadata() throws SQLException;

    // ---- Class queries ---------------------------------------------------

    List<JavaClassRow> classes() throws SQLException;

    Optional<JavaClassRow> findClassById(long classId) throws SQLException;

    List<JavaClassRow> findClassesByName(String name) throws SQLException;

    // ---- Instance queries ------------------------------------------------

    /**
     * Streams every instance whose {@code class_id} equals the given id, in
     * primary-key order. The returned stream owns a JDBC ResultSet and
     * <strong>must be closed</strong> by the caller (try-with-resources).
     */
    Stream<InstanceRow> instances(long classId) throws SQLException;

    Optional<InstanceRow> findInstanceById(long instanceId) throws SQLException;

    long instanceCount(long classId) throws SQLException;

    // ---- GC roots --------------------------------------------------------

    List<GcRootRow> gcRoots() throws SQLException;

    boolean isGcRoot(long instanceId) throws SQLException;

    // ---- String pool -----------------------------------------------------

    Optional<String> findString(long stringId) throws SQLException;

    // ---- Aggregate -------------------------------------------------------

    /**
     * Class histogram: per-class instance count and total shallow size,
     * ordered by total descending. Implemented by SQL aggregation against
     * the {@code instance} table — no per-row Java work.
     */
    List<HistogramRow> classHistogram() throws SQLException;

    // ---- Escape hatch ----------------------------------------------------

    /**
     * The underlying DuckDB connection, for analyzers that need ad-hoc SQL
     * the typed methods don't cover. Callers must not close it; the
     * {@link HeapView} owns the lifecycle.
     */
    Connection connection();

    @Override
    void close();
}
