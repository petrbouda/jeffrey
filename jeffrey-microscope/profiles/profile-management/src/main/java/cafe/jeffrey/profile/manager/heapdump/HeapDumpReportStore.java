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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.manager.heapdump.analysis.BiggestCollectionsAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.BiggestObjectsAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.CachedAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.ClassLoaderHeapAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.CollectionHeapAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.ConsumerReportAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.LeakSuspectsAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.StringHeapAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.ThreadHeapAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Typed JSON I/O for heap-dump analysis sidecar files. Owns the
 * {@link ObjectMapper} and the analysis directory; all heap-dump report
 * persistence flows through here.
 */
public final class HeapDumpReportStore {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpReportStore.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            new ConsumerReportAnalysis());

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
            return Optional.of(OBJECT_MAPPER.readValue(filePath.toFile(), type));
        } catch (JacksonException e) {
            LOG.error("Failed to read analysis file: path={}", filePath, e);
            return Optional.empty();
        }
    }

    public void write(String fileName, Object payload, String displayName) {
        try {
            Files.createDirectories(analysisDir);
            Path filePath = analysisDir.resolve(fileName);
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), payload);
            LOG.info("{} saved: path={}", displayName, filePath);
        } catch (IOException | JacksonException e) {
            LOG.error("Failed to save {}: path={}", displayName, analysisDir, e);
            throw new RuntimeException("Failed to save " + displayName + ": " + e.getMessage(), e);
        }
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
