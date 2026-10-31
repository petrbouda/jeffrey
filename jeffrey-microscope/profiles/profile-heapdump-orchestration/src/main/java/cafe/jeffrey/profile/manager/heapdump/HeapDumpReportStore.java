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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.manager.heapdump.analysis.BiggestCollectionsAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.BiggestObjectsAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.CachedAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.ClassLoaderHeapAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.CollectionHeapAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.ConsumerReportAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.DuplicateDataAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.LeakSuspectsAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.StringHeapAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.ThreadHeapAnalysis;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Typed JSON I/O for heap-dump analysis sidecar files. Uses the shared
 * {@link Json} mapper and owns the analysis directory; all heap-dump report
 * persistence flows through here.
 */
public final class HeapDumpReportStore {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpReportStore.class);

    /**
     * Closed list of every cached-analysis sidecar this store knows about.
     * Used by {@link #deleteAllCachedAnalyses()} to clear the cache without
     * each caller having to list the files itself.
     */
    private static final List<CachedAnalysis<?>> ALL_CACHED_ANALYSES = List.of(
            new StringHeapAnalysis(),
            new ThreadHeapAnalysis(),
            new CollectionHeapAnalysis(),
            new LeakSuspectsAnalysis(),
            new BiggestObjectsAnalysis(),
            new BiggestCollectionsAnalysis(),
            new ClassLoaderHeapAnalysis(),
            new ConsumerReportAnalysis(),
            new DuplicateDataAnalysis());

    private static final String JSON_SUFFIX = ".json";

    /** Between a report's name and the version of the dominator tree it was computed from. */
    private static final String TREE_VERSION_MARK = ".tree-v";

    private static final String ANALYSIS_VERSION_MARK = ".analysis-v";

    private static final int LEGACY_ANALYSIS_VERSION = 1;

    /** A report being written; one a crash left behind is removed with the cached analyses. */
    private static final String TEMPORARY_SUFFIX = ".tmp";

    private final Path analysisDir;

    public HeapDumpReportStore(Path analysisDir) {
        this.analysisDir = analysisDir;
    }

    // --- CachedAnalysis-typed operations --------------------------------
    //
    // The writer passes the actual input tree version, captured under the session lock. Readers
    // require both that tree and the current report algorithm, so changing a report does not force
    // a tree rebuild, and an absent tree cannot be stamped as the running binary's tree version.

    public <T> boolean exists(CachedAnalysis<T> analysis) {
        return read(analysis).isPresent();
    }

    /** The report over the current tree, or — for one that reads the tree when built — its tree-less copy. */
    public <T> Optional<T> read(CachedAnalysis<T> analysis) {
        Optional<T> current = read(storedName(analysis), analysis.type());
        if (current.isPresent() || !analysis.readsTreeWhenBuilt()) {
            return current;
        }
        return read(treelessName(analysis), analysis.type());
    }

    /** Whether what {@link #read} serves is the copy computed without a tree. */
    public <T> boolean readsWithoutTree(CachedAnalysis<T> analysis) {
        return analysis.readsTreeWhenBuilt()
                && read(storedName(analysis), analysis.type()).isEmpty()
                && read(treelessName(analysis), analysis.type()).isPresent();
    }

    public <T> void write(CachedAnalysis<T> analysis, HeapReportVersion version, T report) {
        boolean treeless = analysis.readsTreeWhenBuilt() && HeapReportVersion.withoutTree(analysis).equals(version);
        if (!treeless && !HeapReportVersion.expected(analysis).equals(version)) {
            throw new IllegalArgumentException("Report was not computed from the required versions: " + version);
        }
        String written = storedName(analysis, version);
        write(written, report, analysis.displayName());
        for (String stale : staleNames(analysis)) {
            if (!stale.equals(written)) {
                delete(stale, analysis.displayName());
            }
        }
    }

    public <T> void delete(CachedAnalysis<T> analysis) {
        delete(storedName(analysis), analysis.displayName());
        for (String stale : staleNames(analysis)) {
            delete(stale, analysis.displayName());
        }
    }

    /** A stored but obsolete or unreadable report can be refreshed; a never-requested report cannot. */
    public boolean hasStoredCopy(CachedAnalysis<?> analysis) {
        return Files.exists(analysisDir.resolve(storedName(analysis))) || !staleNames(analysis).isEmpty();
    }

    /** The expected report algorithm and, for retained figures, the tree it must have read. */
    static String storedName(CachedAnalysis<?> analysis) {
        return storedName(analysis, HeapReportVersion.expected(analysis));
    }

    /** Where a report that reads the tree when built is kept while computed without one. */
    static String treelessName(CachedAnalysis<?> analysis) {
        return storedName(analysis, HeapReportVersion.withoutTree(analysis));
    }

    private static String storedName(CachedAnalysis<?> analysis, HeapReportVersion version) {
        // Unchanged reports that never read a tree retain their compatible legacy names.
        if (version.treeVersion() == null && version.analysisVersion() == LEGACY_ANALYSIS_VERSION) {
            return analysis.fileName();
        }
        String tree = version.treeVersion() == null ? "" : TREE_VERSION_MARK + version.treeVersion();
        return baseName(analysis) + ANALYSIS_VERSION_MARK + version.analysisVersion() + tree + JSON_SUFFIX;
    }

    /**
     * The analysis's files from earlier report algorithms or trees, including unversioned files.
     */
    private List<String> staleNames(CachedAnalysis<?> analysis) {
        if (!Files.isDirectory(analysisDir)) {
            return List.of();
        }
        String current = storedName(analysis);
        String versionedPrefix = baseName(analysis) + TREE_VERSION_MARK;
        String analysisPrefix = baseName(analysis) + ANALYSIS_VERSION_MARK;
        List<String> stale = new ArrayList<>();
        try (Stream<Path> files = Files.list(analysisDir)) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> !name.equals(current))
                    .filter(name -> name.equals(analysis.fileName())
                            || ((name.startsWith(versionedPrefix) || name.startsWith(analysisPrefix))
                            && name.endsWith(JSON_SUFFIX)))
                    .forEach(stale::add);
        } catch (IOException e) {
            LOG.warn("Could not list cached analyses: path={} message={}", analysisDir, e.getMessage());
        }
        return stale;
    }

    private static String baseName(CachedAnalysis<?> analysis) {
        String name = analysis.fileName();
        return name.endsWith(JSON_SUFFIX) ? name.substring(0, name.length() - JSON_SUFFIX.length()) : name;
    }

    // --- Raw-fileName operations (for non-CachedAnalysis files) ---------

    public boolean exists(String fileName) {
        return Files.exists(analysisDir.resolve(fileName));
    }

    public <T> Optional<T> read(String fileName, Class<T> type) {
        Path filePath = analysisDir.resolve(fileName);
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(Json.mapper().readValue(filePath.toFile(), type));
        } catch (JacksonException e) {
            // Not an error: an unreadable report reads as not built and is computed again.
            LOG.warn("Unreadable analysis file, read as not built: path={} message={}", filePath, e.getMessage());
            return Optional.empty();
        }
    }

    public void write(String fileName, Object payload, String displayName) {
        Path temporary = null;
        try {
            ensureAnalysisDir();
            Path filePath = analysisDir.resolve(fileName);
            temporary = Files.createTempFile(analysisDir, fileName + ".", TEMPORARY_SUFFIX);
            Json.mapper().writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), payload);
            try {
                Files.move(temporary, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            LOG.info("{} saved: path={}", displayName, filePath);
        } catch (IOException | JacksonException e) {
            LOG.error("Failed to save {}: path={}", displayName, analysisDir, e);
            throw Exceptions.internal("Failed to save " + displayName + ": " + e.getMessage(), e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    LOG.warn("Could not remove temporary report: path={}", temporary, e);
                }
            }
        }
    }

    /**
     * Creates the analysis directory, but only when it is not already there.
     * <p>
     * {@link Files#createDirectories} is idempotent by catching its own
     * {@link java.nio.file.FileAlreadyExistsException}, so calling it on an existing directory
     * succeeds — but it still gets there by attempting the {@code mkdir}, and a recording with
     * {@code jdk.JavaExceptionThrow} enabled captures both that exception and the
     * {@code sun.nio.fs.UnixException} underneath it, stack traces and all. A heap-dump
     * initialization writes one report per analysis stage, so the unguarded call turned a routine
     * save into a dozen throws in the trace. {@link Files#isDirectory} answers from a stat and
     * throws nothing.
     */
    private void ensureAnalysisDir() throws IOException {
        if (Files.isDirectory(analysisDir)) {
            return;
        }
        Files.createDirectories(analysisDir);
    }

    public void delete(String fileName, String displayName) {
        Path filePath = analysisDir.resolve(fileName);
        if (!Files.exists(filePath)) {
            return;
        }
        try {
            Files.delete(filePath);
            LOG.info("{} deleted: path={}", displayName, filePath);
        } catch (IOException e) {
            LOG.error("Failed to delete {}: path={}", displayName, filePath, e);
        }
    }

    /**
     * Delete every cached-analysis sidecar this store knows about. Used by
     * the upload service when clearing the analysis cache.
     */
    public void deleteAllCachedAnalyses() {
        for (CachedAnalysis<?> analysis : ALL_CACHED_ANALYSES) {
            delete(analysis);
        }
        deleteTemporaryReports();
    }

    private void deleteTemporaryReports() {
        if (!Files.isDirectory(analysisDir)) {
            return;
        }
        List<String> prefixes = ALL_CACHED_ANALYSES.stream().map(HeapDumpReportStore::baseName).toList();
        try (Stream<Path> files = Files.list(analysisDir)) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(TEMPORARY_SUFFIX))
                    .filter(name -> prefixes.stream().anyMatch(name::startsWith))
                    .forEach(name -> delete(name, "Temporary report"));
        } catch (IOException e) {
            LOG.warn("Could not list temporary reports: path={} message={}", analysisDir, e.getMessage());
        }
    }
}
