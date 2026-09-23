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
import java.util.List;
import java.util.Optional;

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

    private final Path analysisDir;

    public HeapDumpReportStore(Path analysisDir) {
        this.analysisDir = analysisDir;
    }

    // --- CachedAnalysis-typed operations --------------------------------

    public <T> boolean exists(CachedAnalysis<T> analysis) {
        return Files.exists(analysisDir.resolve(analysis.fileName()));
    }

    public <T> Optional<T> read(CachedAnalysis<T> analysis) {
        return read(analysis.fileName(), analysis.type());
    }

    public <T> void write(CachedAnalysis<T> analysis, T report) {
        write(analysis.fileName(), report, analysis.displayName());
    }

    public <T> void delete(CachedAnalysis<T> analysis) {
        delete(analysis.fileName(), analysis.displayName());
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
            return Optional.of(Json.mapper().readValue(filePath.toFile(), type));
        } catch (JacksonException e) {
            LOG.error("Failed to read analysis file: path={}", filePath, e);
            return Optional.empty();
        }
    }

    public void write(String fileName, Object payload, String displayName) {
        try {
            ensureAnalysisDir();
            Path filePath = analysisDir.resolve(fileName);
            Json.mapper().writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), payload);
            LOG.info("{} saved: path={}", displayName, filePath);
        } catch (IOException | JacksonException e) {
            LOG.error("Failed to save {}: path={}", displayName, analysisDir, e);
            throw Exceptions.internal("Failed to save " + displayName + ": " + e.getMessage(), e);
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
    }
}
