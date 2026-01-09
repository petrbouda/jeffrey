/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.manager;

import org.netbeans.lib.profiler.heap.Heap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.HeapLoader;
import pbouda.jeffrey.profile.heapdump.analyzer.ClassHistogramAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.GCRootAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.HeapSummaryAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.OQLQueryExecutor;
import pbouda.jeffrey.profile.heapdump.analyzer.ThreadAnalyzer;
import pbouda.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import pbouda.jeffrey.profile.heapdump.model.GCRootSummary;
import pbouda.jeffrey.profile.heapdump.model.HeapSummary;
import pbouda.jeffrey.profile.heapdump.model.HeapThreadInfo;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryRequest;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryResult;
import pbouda.jeffrey.profile.heapdump.model.SortBy;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.repository.FileExtensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of HeapDumpManager that provides heap dump analysis capabilities.
 */
public class HeapDumpManagerImpl implements HeapDumpManager {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpManagerImpl.class);

    private final ProfileInfo profileInfo;
    private final HeapLoader heapLoader;
    private final AdditionalFilesManager additionalFilesManager;

    // Analyzers
    private final HeapSummaryAnalyzer summaryAnalyzer;
    private final ClassHistogramAnalyzer histogramAnalyzer;
    private final OQLQueryExecutor queryExecutor;
    private final ThreadAnalyzer threadAnalyzer;
    private final GCRootAnalyzer gcRootAnalyzer;

    public HeapDumpManagerImpl(
            ProfileInfo profileInfo,
            HeapLoader heapLoader,
            AdditionalFilesManager additionalFilesManager) {

        this.profileInfo = profileInfo;
        this.heapLoader = heapLoader;
        this.additionalFilesManager = additionalFilesManager;

        // Initialize analyzers
        this.summaryAnalyzer = new HeapSummaryAnalyzer();
        this.histogramAnalyzer = new ClassHistogramAnalyzer();
        this.queryExecutor = new OQLQueryExecutor();
        this.threadAnalyzer = new ThreadAnalyzer();
        this.gcRootAnalyzer = new GCRootAnalyzer();
    }

    @Override
    public boolean heapDumpExists() {
        return additionalFilesManager.heapDumpExists();
    }

    @Override
    public boolean isCacheReady() {
        Optional<Path> heapPath = additionalFilesManager.getHeapDumpPath();
        if (heapPath.isEmpty()) {
            return false;
        }

        Path path = heapPath.get();
        String fileName = path.getFileName().toString().toLowerCase();

        // For gzipped files, check if decompressed version exists
        if (fileName.endsWith(FileExtensions.HPROF_GZ)) {
            String decompressedName = path.getFileName().toString();
            decompressedName = decompressedName.substring(0, decompressedName.length() - 3);
            path = path.resolveSibling(decompressedName);
        }

        // Check if .nbcache directory exists
        Path cachePath = path.resolveSibling(path.getFileName() + ".nbcache");
        return Files.isDirectory(cachePath);
    }

    @Override
    public HeapSummary getSummary() {
        return getHeap()
                .map(summaryAnalyzer::analyze)
                .orElse(null);
    }

    @Override
    public List<ClassHistogramEntry> getClassHistogram(int topN) {
        return getClassHistogram(topN, SortBy.SIZE);
    }

    @Override
    public List<ClassHistogramEntry> getClassHistogram(int topN, SortBy sortBy) {
        return getHeap()
                .map(heap -> histogramAnalyzer.analyze(heap, topN, sortBy))
                .orElse(List.of());
    }

    @Override
    public OQLQueryResult executeQuery(OQLQueryRequest request) {
        Optional<Heap> heapOpt = getHeap();
        if (heapOpt.isEmpty()) {
            return OQLQueryResult.error("Heap dump not available", 0);
        }
        return queryExecutor.execute(heapOpt.get(), request);
    }

    @Override
    public List<HeapThreadInfo> getThreads() {
        return getHeap()
                .map(threadAnalyzer::analyze)
                .orElse(List.of());
    }

    @Override
    public GCRootSummary getGCRootSummary() {
        return getHeap()
                .map(gcRootAnalyzer::analyze)
                .orElse(null);
    }

    @Override
    public void unloadHeap() {
        Optional<Path> heapPath = additionalFilesManager.getHeapDumpPath();
        heapPath.ifPresent(path -> {
            heapLoader.unload(path);
            LOG.info("Heap unloaded: profileId={} path={}", profileInfo.id(), path);
        });
    }

    @Override
    public void deleteCache() {
        Optional<Path> heapPath = additionalFilesManager.getHeapDumpPath();
        if (heapPath.isEmpty()) {
            return;
        }

        Path path = heapPath.get();
        String fileName = path.getFileName().toString().toLowerCase();

        // For gzipped files, resolve to decompressed path
        if (fileName.endsWith(FileExtensions.HPROF_GZ)) {
            String decompressedName = path.getFileName().toString();
            decompressedName = decompressedName.substring(0, decompressedName.length() - 3);
            path = path.resolveSibling(decompressedName);
        }

        // Delete .nbcache directory
        Path cachePath = path.resolveSibling(path.getFileName() + ".nbcache");
        if (Files.isDirectory(cachePath)) {
            try {
                deleteDirectory(cachePath);
                LOG.info("Cache deleted: profileId={} path={}", profileInfo.id(), cachePath);
            } catch (IOException e) {
                LOG.error("Failed to delete cache: profileId={} path={}", profileInfo.id(), cachePath, e);
            }
        }
    }

    @Override
    public void deleteHeapDump() {
        Optional<Path> heapPath = additionalFilesManager.getHeapDumpPath();
        if (heapPath.isEmpty()) {
            return;
        }

        // Unload heap from memory first
        heapLoader.unload(heapPath.get());

        Path path = heapPath.get();
        String fileName = path.getFileName().toString().toLowerCase();

        Path hprofPath;
        Path gzPath = null;

        if (fileName.endsWith(FileExtensions.HPROF_GZ)) {
            // Original is gzipped
            gzPath = path;
            String decompressedName = path.getFileName().toString();
            decompressedName = decompressedName.substring(0, decompressedName.length() - 3);
            hprofPath = path.resolveSibling(decompressedName);
        } else {
            // Original is .hprof
            hprofPath = path;
            // Check if .gz version exists
            Path potentialGz = path.resolveSibling(path.getFileName() + ".gz");
            if (Files.exists(potentialGz)) {
                gzPath = potentialGz;
            }
        }

        // Delete .nbcache directory
        Path cachePath = hprofPath.resolveSibling(hprofPath.getFileName() + ".nbcache");
        if (Files.isDirectory(cachePath)) {
            try {
                deleteDirectory(cachePath);
                LOG.info("Cache deleted: profileId={} path={}", profileInfo.id(), cachePath);
            } catch (IOException e) {
                LOG.error("Failed to delete cache: profileId={} path={}", profileInfo.id(), cachePath, e);
            }
        }

        // Delete .hprof file
        if (Files.exists(hprofPath)) {
            try {
                Files.delete(hprofPath);
                LOG.info("Heap dump deleted: profileId={} path={}", profileInfo.id(), hprofPath);
            } catch (IOException e) {
                LOG.error("Failed to delete heap dump: profileId={} path={}", profileInfo.id(), hprofPath, e);
            }
        }

        // Delete .hprof.gz file
        if (gzPath != null && Files.exists(gzPath)) {
            try {
                Files.delete(gzPath);
                LOG.info("Compressed heap dump deleted: profileId={} path={}", profileInfo.id(), gzPath);
            } catch (IOException e) {
                LOG.error("Failed to delete compressed heap dump: profileId={} path={}", profileInfo.id(), gzPath, e);
            }
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            LOG.warn("Failed to delete file: path={}", p, e);
                        }
                    });
        }
    }

    private Optional<Heap> getHeap() {
        Optional<Path> heapPath = additionalFilesManager.getHeapDumpPath();
        if (heapPath.isEmpty()) {
            LOG.debug("No heap dump available: profileId={}", profileInfo.id());
            return Optional.empty();
        }

        Optional<Heap> heap = heapLoader.load(heapPath.get());
        if (heap.isEmpty()) {
            LOG.warn("Failed to load heap dump: profileId={} path={}", profileInfo.id(), heapPath.get());
        }
        return heap;
    }
}
