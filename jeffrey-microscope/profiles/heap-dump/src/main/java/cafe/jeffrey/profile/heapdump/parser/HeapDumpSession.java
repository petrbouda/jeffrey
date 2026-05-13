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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cafe.jeffrey.profile.heapdump.model.SubPhaseTiming;

/**
 * Bundle holding the open {@link HprofMappedFile} and {@link HeapView} for a
 * single heap dump, with paired lifecycle. Closing the session closes both.
 *
 * Replaces the load-Heap pattern callers used with NetBeans: instead of
 * {@code Heap heap = HeapFactory.createHeap(file)}, callers do
 *
 * <pre>{@code
 * try (HeapDumpSession session = HeapDumpSession.openOrBuild(path, clock)) {
 *     HeapView view = session.view();
 *     // run any heapview analyzer against view ...
 * }
 * }</pre>
 *
 * On first call for a given dump, builds the {@code .idx.duckdb} sibling.
 * Subsequent calls just open the existing index. Dominator-tree construction
 * is opt-in via {@link #buildDominatorTreeIfNeeded()} so cheap analyzers
 * don't pay the cost.
 */
public final class HeapDumpSession implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpSession.class);

    private final HprofMappedFile hprof;
    private HeapView view;
    private final Path indexDbPath;
    /**
     * Per-phase timings captured the last time {@link HprofIndex#build} ran for
     * this session — empty when an existing index was reused. Surfaced to the
     * UI's "Building indexes" accordion via {@code HeapDumpManager.initialize}.
     */
    private final List<SubPhaseTiming> lastBuildSubPhases;

    private HeapDumpSession(
            HprofMappedFile hprof,
            HeapView view,
            Path indexDbPath,
            List<SubPhaseTiming> lastBuildSubPhases) {
        this.hprof = hprof;
        this.view = view;
        this.indexDbPath = indexDbPath;
        this.lastBuildSubPhases = lastBuildSubPhases;
    }

    /**
     * Opens the dump and returns a session. Builds the {@code .idx.duckdb}
     * index sibling on first access; reuses an existing one if present and
     * not stale (mtime check).
     */
    public static HeapDumpSession openOrBuild(Path hprofPath, Clock clock) throws IOException, SQLException {
        if (hprofPath == null) {
            throw new IllegalArgumentException("hprofPath must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (!Files.exists(hprofPath)) {
            throw new IOException("Heap dump file does not exist: path=" + hprofPath);
        }

        HprofMappedFile hprof = HprofMappedFile.open(hprofPath);
        Path indexDbPath = HeapDumpIndexPaths.indexFor(hprofPath);

        try {
            List<SubPhaseTiming> subPhases = List.of();
            if (needsRebuild(hprofPath, indexDbPath)) {
                LOG.debug("Building heap dump index: hprof={} index={}", hprofPath, indexDbPath);
                HprofIndex.IndexResult result = HprofIndex.build(hprof, indexDbPath, clock);
                subPhases = result.subPhases();
            } else {
                LOG.debug("Reusing heap dump index: index={}", indexDbPath);
            }
            HeapView view = HeapView.open(indexDbPath, hprof);
            return new HeapDumpSession(hprof, view, indexDbPath, subPhases);
        } catch (Throwable t) {
            try {
                hprof.close();
            } catch (RuntimeException ignored) {
            }
            throw t;
        }
    }

    /**
     * Builds the dominator tree + retained sizes if not already populated.
     * Idempotent — checks {@link HeapView#hasDominatorTree()} first. Returns
     * the {@link DominatorTreeBuilder.BuildResult} (with its sub-phase
     * timings) when a build actually ran; {@link Optional#empty()} when the
     * tree was already present and nothing was rebuilt.
     *
     * Implementation note: DuckDB doesn't permit a read-write builder
     * connection while a read-only view is open against the same file.
     * The current view is closed before the build and re-opened afterwards
     * so the same {@link HeapDumpSession} stays usable.
     */
    public Optional<DominatorTreeBuilder.BuildResult> buildDominatorTreeIfNeeded()
            throws SQLException, IOException {
        if (view.hasDominatorTree()) {
            return Optional.empty();
        }
        LOG.debug("Building dominator tree: index={}", indexDbPath);
        view.close();
        try {
            return Optional.of(DominatorTreeBuilder.build(indexDbPath));
        } finally {
            view = HeapView.open(indexDbPath, hprof);
        }
    }

    /**
     * Per-phase index-build timings captured the last time this session ran
     * {@link HprofIndex#build}. Empty list when the existing index was reused.
     */
    public List<SubPhaseTiming> lastBuildSubPhases() {
        return lastBuildSubPhases;
    }

    public HeapView view() {
        return view;
    }

    public HprofMappedFile hprof() {
        return hprof;
    }

    public Path indexDbPath() {
        return indexDbPath;
    }

    @Override
    public void close() {
        try {
            view.close();
        } catch (RuntimeException ignored) {
        }
        try {
            hprof.close();
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Returns true when the index is missing or older than the source dump
     * (mtime check). Cheap to call.
     */
    private static boolean needsRebuild(Path hprof, Path indexDb) throws IOException {
        if (!Files.exists(indexDb)) {
            return true;
        }
        long hprofMtime = Files.getLastModifiedTime(hprof).toMillis();
        long indexMtime = Files.getLastModifiedTime(indexDb).toMillis();
        return indexMtime < hprofMtime;
    }
}
