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
import pbouda.jeffrey.profile.heapdump.analyzer.InstanceDetailAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.InstanceTreeAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.OQLQueryExecutor;
import pbouda.jeffrey.profile.heapdump.analyzer.StringAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.ThreadAnalyzer;
import pbouda.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import pbouda.jeffrey.profile.heapdump.model.GCRootSummary;
import pbouda.jeffrey.profile.heapdump.model.HeapSummary;
import pbouda.jeffrey.profile.heapdump.model.HeapThreadInfo;
import pbouda.jeffrey.profile.heapdump.model.InstanceDetail;
import pbouda.jeffrey.profile.heapdump.model.InstanceTreeResponse;
import pbouda.jeffrey.profile.heapdump.model.JvmStringFlag;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryRequest;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryResult;
import pbouda.jeffrey.profile.heapdump.model.SortBy;
import pbouda.jeffrey.profile.heapdump.model.StringAnalysisReport;
import pbouda.jeffrey.profile.heapdump.model.ThreadAnalysisReport;
import pbouda.jeffrey.profile.common.event.GarbageCollectorType;
import pbouda.jeffrey.provider.profile.model.JvmFlag;
import pbouda.jeffrey.provider.profile.repository.ProfileEventRepository;
import pbouda.jeffrey.shared.common.exception.ErrorCode;
import pbouda.jeffrey.shared.common.exception.ErrorType;
import pbouda.jeffrey.shared.common.exception.JeffreyException;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.repository.FileExtensions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of HeapDumpManager that provides heap dump analysis capabilities.
 */
public class HeapDumpManagerImpl implements HeapDumpManager {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpManagerImpl.class);

    private static final Map<String, String> FLAG_DESCRIPTIONS = Map.ofEntries(
            Map.entry("UseStringDeduplication", "Enable string deduplication during GC"),
            Map.entry("StringDeduplicationAgeThreshold", "GC cycles before string becomes dedup candidate"),
            Map.entry("UseG1GC", "Enable G1 Garbage Collector"),
            Map.entry("UseZGC", "Enable Z Garbage Collector"),
            Map.entry("UseShenandoahGC", "Enable Shenandoah Garbage Collector"),
            Map.entry("UseParallelGC", "Enable Parallel Garbage Collector"),
            Map.entry("UseSerialGC", "Enable Serial Garbage Collector"),
            Map.entry("CompactStrings", "Use compact representation for Latin-1 strings (Java 9+)"),
            Map.entry("OptimizeStringConcat", "Optimize string concatenation operations")
    );

    private final ProfileInfo profileInfo;
    private final HeapLoader heapLoader;
    private final AdditionalFilesManager additionalFilesManager;
    private final ProfileEventRepository eventRepository;

    // Analyzers
    private final HeapSummaryAnalyzer summaryAnalyzer;
    private final ClassHistogramAnalyzer histogramAnalyzer;
    private final OQLQueryExecutor queryExecutor;
    private final ThreadAnalyzer threadAnalyzer;
    private final GCRootAnalyzer gcRootAnalyzer;
    private final StringAnalyzer stringAnalyzer;
    private final InstanceDetailAnalyzer instanceDetailAnalyzer;
    private final InstanceTreeAnalyzer instanceTreeAnalyzer;

    public HeapDumpManagerImpl(
            ProfileInfo profileInfo,
            HeapLoader heapLoader,
            AdditionalFilesManager additionalFilesManager,
            ProfileEventRepository eventRepository) {

        this.profileInfo = profileInfo;
        this.heapLoader = heapLoader;
        this.additionalFilesManager = additionalFilesManager;
        this.eventRepository = eventRepository;

        // Initialize analyzers
        this.summaryAnalyzer = new HeapSummaryAnalyzer();
        this.histogramAnalyzer = new ClassHistogramAnalyzer();
        this.queryExecutor = new OQLQueryExecutor();
        this.threadAnalyzer = new ThreadAnalyzer();
        this.gcRootAnalyzer = new GCRootAnalyzer();
        this.stringAnalyzer = new StringAnalyzer();
        this.instanceDetailAnalyzer = new InstanceDetailAnalyzer();
        this.instanceTreeAnalyzer = new InstanceTreeAnalyzer();
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
        Optional<Path> heapPath = additionalFilesManager.getHeapDumpPath();
        if (heapPath.isEmpty()) {
            return null; // No heap dump exists
        }

        Optional<Heap> heap = heapLoader.load(heapPath.get());
        if (heap.isEmpty()) {
            // Heap exists but can't be loaded - corrupted
            deleteCorruptedHeapDump(heapPath.get());
            throw new JeffreyException(ErrorType.CLIENT, ErrorCode.HEAP_DUMP_CORRUPTED,
                    "The heap dump file could not be loaded. The file may be corrupted or truncated.");
        }

        return summaryAnalyzer.analyze(heap.get());
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
        return getThreads(false);
    }

    @Override
    public List<HeapThreadInfo> getThreads(boolean includeRetainedSize) {
        return getHeap()
                .map(heap -> threadAnalyzer.analyze(heap, includeRetainedSize))
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
        heapPath.ifPresent(heapLoader::unload);
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
        try {
            FileSystemUtils.removeDirectory(cachePath);
        } catch (RuntimeException e) {
            LOG.error("Failed to delete cache: profileId={} path={}", profileInfo.id(), cachePath, e);
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
        try {
            FileSystemUtils.removeDirectory(cachePath);
        } catch (RuntimeException e) {
            LOG.error("Failed to delete cache: profileId={} path={}", profileInfo.id(), cachePath, e);
        }

        // Delete .hprof file
        if (Files.exists(hprofPath)) {
            try {
                Files.delete(hprofPath);
            } catch (IOException e) {
                LOG.error("Failed to delete heap dump: profileId={} path={}", profileInfo.id(), hprofPath, e);
            }
        }

        // Delete .hprof.gz file
        if (gzPath != null && Files.exists(gzPath)) {
            try {
                Files.delete(gzPath);
            } catch (IOException e) {
                LOG.error("Failed to delete compressed heap dump: profileId={} path={}", profileInfo.id(), gzPath, e);
            }
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

    @Override
    public void uploadHeapDump(InputStream inputStream, String filename) {
        // Validate filename extension
        String lowerFilename = filename.toLowerCase();
        if (!lowerFilename.endsWith("." + FileExtensions.HPROF) &&
                !lowerFilename.endsWith("." + FileExtensions.HPROF_GZ)) {
            throw new IllegalArgumentException("Invalid file type. Only .hprof and .hprof.gz files are supported.");
        }

        Path heapDumpAnalysisPath = additionalFilesManager.getHeapDumpAnalysisPath();

        // Clean up any existing files/cache before uploading new heap dump
        try {
            FileSystemUtils.removeDirectory(heapDumpAnalysisPath);
        } catch (RuntimeException e) {
            LOG.warn("Failed to clean up existing heap dump directory: profileId={} path={}", profileInfo.id(), heapDumpAnalysisPath, e);
        }

        Path targetPath = heapDumpAnalysisPath.resolve(filename);

        try {
            // Create the directory if it doesn't exist
            Files.createDirectories(heapDumpAnalysisPath);

            // Save the file
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

            LOG.info("Heap dump uploaded: profileId={} path={}", profileInfo.id(), targetPath);
        } catch (IOException e) {
            LOG.error("Failed to upload heap dump: profileId={} filename={}", profileInfo.id(), filename, e);
            throw new RuntimeException("Failed to upload heap dump: " + e.getMessage(), e);
        }
    }

    private void deleteCorruptedHeapDump(Path path) {
        Path heapDumpAnalysisPath = additionalFilesManager.getHeapDumpAnalysisPath();
        try {
            FileSystemUtils.removeDirectory(heapDumpAnalysisPath);
            LOG.info("Deleted corrupted heap dump analysis folder: profileId={} path={}", profileInfo.id(), heapDumpAnalysisPath);
        } catch (RuntimeException e) {
            LOG.warn("Failed to delete corrupted heap dump analysis folder: profileId={} path={}", profileInfo.id(), heapDumpAnalysisPath, e);
        }
    }

    @Override
    public boolean stringAnalysisExists() {
        return additionalFilesManager.stringAnalysisExists();
    }

    @Override
    public StringAnalysisReport getStringAnalysis() {
        return additionalFilesManager.getStringAnalysis().orElse(null);
    }

    @Override
    public void runStringAnalysis(int topN) {
        getHeap().ifPresent(heap -> {
            // Analyze heap for string deduplication
            StringAnalysisReport heapAnalysis = stringAnalyzer.analyze(heap, topN);

            // Get JVM flags related to strings from JFR events
            List<JvmStringFlag> jvmFlags = getStringRelatedJvmFlags();

            // Create complete report with JVM flags
            StringAnalysisReport report = new StringAnalysisReport(
                    heapAnalysis.totalStrings(),
                    heapAnalysis.totalStringShallowSize(),
                    heapAnalysis.uniqueArrays(),
                    heapAnalysis.sharedArrays(),
                    heapAnalysis.totalSharedStrings(),
                    heapAnalysis.memorySavedByDedup(),
                    heapAnalysis.potentialSavings(),
                    heapAnalysis.alreadyDeduplicated(),
                    heapAnalysis.opportunities(),
                    jvmFlags
            );

            additionalFilesManager.saveStringAnalysis(report);
        });
    }

    private List<JvmStringFlag> getStringRelatedJvmFlags() {
        List<JvmFlag> flags = eventRepository.getStringRelatedFlags();
        return flags.stream()
                // Filter out disabled GC flags (only show enabled GC)
                .filter(flag -> !GarbageCollectorType.isGcFlag(flag.name()) || "true".equals(flag.value()))
                .map(flag -> new JvmStringFlag(
                        flag.name(),
                        flag.value(),
                        flag.type(),
                        flag.origin(),
                        FLAG_DESCRIPTIONS.getOrDefault(flag.name(), "")
                ))
                .toList();
    }

    @Override
    public void deleteStringAnalysis() {
        additionalFilesManager.deleteStringAnalysis();
    }

    @Override
    public boolean threadAnalysisExists() {
        return additionalFilesManager.threadAnalysisExists();
    }

    @Override
    public ThreadAnalysisReport getThreadAnalysis() {
        return additionalFilesManager.getThreadAnalysis().orElse(null);
    }

    @Override
    public void runThreadAnalysis() {
        getHeap().ifPresent(heap -> {
            // Analyze threads with retained size calculation (expensive)
            List<HeapThreadInfo> threads = threadAnalyzer.analyze(heap, true);

            // Calculate summary statistics
            int daemonCount = (int) threads.stream().filter(HeapThreadInfo::daemon).count();
            int userCount = threads.size() - daemonCount;
            long totalRetained = threads.stream()
                    .mapToLong(t -> t.retainedSize() != null ? t.retainedSize() : 0L)
                    .sum();

            ThreadAnalysisReport report = new ThreadAnalysisReport(
                    threads.size(),
                    daemonCount,
                    userCount,
                    totalRetained,
                    threads
            );

            additionalFilesManager.saveThreadAnalysis(report);
        });
    }

    @Override
    public void deleteThreadAnalysis() {
        additionalFilesManager.deleteThreadAnalysis();
    }

    @Override
    public InstanceDetail getInstanceDetail(long objectId, boolean includeRetainedSize) {
        return getHeap()
                .map(heap -> instanceDetailAnalyzer.analyze(heap, objectId, includeRetainedSize))
                .orElse(null);
    }

    @Override
    public InstanceTreeResponse getReferrers(long objectId, int limit, int offset) {
        return getHeap()
                .map(heap -> instanceTreeAnalyzer.getReferrers(heap, objectId, limit, offset))
                .orElse(InstanceTreeResponse.notFound());
    }

    @Override
    public InstanceTreeResponse getReachables(long objectId, int limit, int offset) {
        return getHeap()
                .map(heap -> instanceTreeAnalyzer.getReachables(heap, objectId, limit, offset))
                .orElse(InstanceTreeResponse.notFound());
    }
}
