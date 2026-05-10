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
     * applied here. Field-value reading methods will throw because no .hprof
     * is attached.
     */
    static HeapView open(Path indexDbPath) throws SQLException, IOException {
        return DuckDbHeapView.open(indexDbPath, null);
    }

    /**
     * Opens a {@link HeapView} with both the index DB and the source .hprof
     * attached. Field-value reading and any operation that touches raw
     * instance bytes requires this overload.
     */
    static HeapView open(Path indexDbPath, HprofMappedFile hprof) throws SQLException, IOException {
        return DuckDbHeapView.open(indexDbPath, hprof);
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

    /** Total instances across the whole heap. */
    long totalInstanceCount() throws SQLException;

    /** Sum of {@code shallow_size} across all instances. */
    long totalShallowSize() throws SQLException;

    long classCount() throws SQLException;

    // ---- GC roots --------------------------------------------------------

    List<GcRootRow> gcRoots() throws SQLException;

    boolean isGcRoot(long instanceId) throws SQLException;

    long gcRootCount() throws SQLException;

    // ---- Reference graph -------------------------------------------------

    /** All outbound references from {@code instanceId}. */
    List<OutboundRefRow> outboundRefs(long instanceId) throws SQLException;

    /** All inbound references to {@code instanceId}. Cheap thanks to the index on target_id. */
    List<OutboundRefRow> inboundRefs(long instanceId) throws SQLException;

    /** Count of outbound references — cheap whole-graph metric. */
    long outboundRefCount() throws SQLException;

    // ---- Dominator tree + retained size (populated by DominatorTreeBuilder) ----

    /**
     * Returns the immediate dominator id of {@code instanceId}, or 0L when
     * the instance is directly rooted at the virtual root, or -1 when no
     * dominator entry exists (dominator tree not built / instance unreachable).
     */
    long dominatorOf(long instanceId) throws SQLException;

    /** Retained size in bytes, or 0 if not computed. */
    long retainedSize(long instanceId) throws SQLException;

    /** True iff the dominator + retained_size tables have been populated. */
    boolean hasDominatorTree() throws SQLException;

    // ---- Class fields + instance values ----------------------------------

    /**
     * The instance field descriptors for the given class (this class only,
     * not inherited), in declaration order.
     */
    List<InstanceFieldDescriptor> instanceFields(long classId) throws SQLException;

    /**
     * The full instance field list for the class, walked most-derived-first
     * across the super-class chain. Matches the layout of an INSTANCE_DUMP's
     * field byte block.
     */
    List<InstanceFieldDescriptor> instanceFieldsWithChain(long classId) throws SQLException;

    /**
     * Decodes all instance field values for the given object. Requires that
     * the {@link HeapView} was opened with an attached .hprof file
     * ({@link #open(Path, HprofMappedFile)}); otherwise throws
     * {@link IllegalStateException}.
     */
    List<InstanceFieldValue> readInstanceFields(long instanceId) throws SQLException;

    /**
     * Returns the raw payload bytes of a PRIMITIVE_ARRAY_DUMP. Caller decodes
     * according to the instance's {@code primitiveType}. Requires an attached
     * .hprof. Returns empty bytes if the instance is not a primitive array.
     */
    byte[] readPrimitiveArrayBytes(long instanceId) throws SQLException;

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
