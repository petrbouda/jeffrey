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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.netbeans.lib.profiler.heap.Heap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.HeapLoader;
import pbouda.jeffrey.profile.heapdump.analyzer.ClassHistogramAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.ClassInstanceBrowserAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.CollectionAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.CompressedOopsCorrector;
import pbouda.jeffrey.profile.heapdump.analyzer.DominatorTreeAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.GCRootAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.HeapSummaryAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.InstanceDetailAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.InstanceTreeAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.LeakSuspectsAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.OQLQueryExecutor;
import pbouda.jeffrey.profile.heapdump.analyzer.PathToGCRootAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.StringAnalyzer;
import pbouda.jeffrey.profile.heapdump.analyzer.ThreadAnalyzer;
import pbouda.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import pbouda.jeffrey.profile.heapdump.model.ClassInstancesResponse;
import pbouda.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import pbouda.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import pbouda.jeffrey.profile.heapdump.model.GCRootPath;
import pbouda.jeffrey.profile.heapdump.model.GCRootSummary;
import pbouda.jeffrey.profile.heapdump.model.HeapDumpConfig;
import pbouda.jeffrey.profile.heapdump.model.HeapSummary;
import pbouda.jeffrey.profile.heapdump.model.HeapThreadInfo;
import pbouda.jeffrey.profile.heapdump.model.InstanceDetail;
import pbouda.jeffrey.profile.heapdump.model.InstanceTreeResponse;
import pbouda.jeffrey.profile.heapdump.model.JvmStringFlag;
import pbouda.jeffrey.profile.heapdump.model.BiggestObjectEntry;
import pbouda.jeffrey.profile.heapdump.model.BiggestObjectsReport;
import pbouda.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryRequest;
import pbouda.jeffrey.profile.heapdump.model.OQLQueryResult;
import pbouda.jeffrey.profile.heapdump.model.SortBy;
import pbouda.jeffrey.profile.heapdump.model.StringAnalysisReport;
import pbouda.jeffrey.profile.heapdump.model.ThreadAnalysisReport;
import pbouda.jeffrey.profile.common.event.GCHeapConfiguration;
import pbouda.jeffrey.profile.common.event.GarbageCollectorType;
import pbouda.jeffrey.provider.profile.model.JvmFlag;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.Type;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STRING_ANALYSIS_FILE = "string-analysis.json";
    private static final String THREAD_ANALYSIS_FILE = "thread-analysis.json";
    private static final String COLLECTION_ANALYSIS_FILE = "collection-analysis.json";
    private static final String BIGGEST_OBJECTS_FILE = "biggest-objects.json";
    private static final String LEAK_SUSPECTS_FILE = "leak-suspects.json";
    private static final String HEAP_DUMP_CONFIG_FILE = "heap-dump-config.json";

    private static final long COMPRESSED_OOPS_MAX_HEAP = 32L * 1024 * 1024 * 1024;

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
    private final Path heapDumpAnalysisPath;

    // Analyzers
    private final HeapSummaryAnalyzer summaryAnalyzer;
    private final ClassHistogramAnalyzer histogramAnalyzer;
    private final OQLQueryExecutor queryExecutor;
    private final ThreadAnalyzer threadAnalyzer;
    private final GCRootAnalyzer gcRootAnalyzer;
    private final StringAnalyzer stringAnalyzer;
    private final InstanceDetailAnalyzer instanceDetailAnalyzer;
    private final InstanceTreeAnalyzer instanceTreeAnalyzer;
    private final PathToGCRootAnalyzer pathToGCRootAnalyzer;
    private final DominatorTreeAnalyzer dominatorTreeAnalyzer;
    private final CollectionAnalyzer collectionAnalyzer;
    private final ClassInstanceBrowserAnalyzer classInstanceBrowserAnalyzer;
    private final LeakSuspectsAnalyzer leakSuspectsAnalyzer;

    public HeapDumpManagerImpl(
            ProfileInfo profileInfo,
            HeapLoader heapLoader,
            AdditionalFilesManager additionalFilesManager,
            ProfileEventRepository eventRepository) {

        this.profileInfo = profileInfo;
        this.heapLoader = heapLoader;
        this.additionalFilesManager = additionalFilesManager;
        this.eventRepository = eventRepository;
        this.heapDumpAnalysisPath = additionalFilesManager.getHeapDumpAnalysisPath();

        // Initialize analyzers
        this.summaryAnalyzer = new HeapSummaryAnalyzer();
        this.histogramAnalyzer = new ClassHistogramAnalyzer();
        this.queryExecutor = new OQLQueryExecutor();
        this.threadAnalyzer = new ThreadAnalyzer();
        this.gcRootAnalyzer = new GCRootAnalyzer();
        this.stringAnalyzer = new StringAnalyzer();
        this.instanceDetailAnalyzer = new InstanceDetailAnalyzer();
        this.instanceTreeAnalyzer = new InstanceTreeAnalyzer();
        this.pathToGCRootAnalyzer = new PathToGCRootAnalyzer();
        this.dominatorTreeAnalyzer = new DominatorTreeAnalyzer();
        this.collectionAnalyzer = new CollectionAnalyzer();
        this.classInstanceBrowserAnalyzer = new ClassInstanceBrowserAnalyzer();
        this.leakSuspectsAnalyzer = new LeakSuspectsAnalyzer();
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

        OopsConfig oops = resolveOopsConfig(heap.get());
        return summaryAnalyzer.analyze(heap.get(), oops.compressedOops, oops.totalOvercount);
    }

    @Override
    public List<ClassHistogramEntry> getClassHistogram(int topN) {
        return getClassHistogram(topN, SortBy.SIZE);
    }

    @Override
    public List<ClassHistogramEntry> getClassHistogram(int topN, SortBy sortBy) {
        return getHeap().map(heap -> {
            OopsConfig oops = resolveOopsConfig(heap);
            return histogramAnalyzer.analyze(heap, topN, sortBy, oops.compressedOops, oops.correctionRatio);
        }).orElse(List.of());
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

        // Delete all pre-computed analysis files
        deleteJsonFile(STRING_ANALYSIS_FILE, "String analysis");
        deleteJsonFile(THREAD_ANALYSIS_FILE, "Thread analysis");
        deleteJsonFile(COLLECTION_ANALYSIS_FILE, "Collection analysis");
        deleteJsonFile(LEAK_SUSPECTS_FILE, "Leak suspects");
        deleteJsonFile(BIGGEST_OBJECTS_FILE, "Biggest objects");
        deleteJsonFile(HEAP_DUMP_CONFIG_FILE, "Heap dump config");
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

    private record OopsConfig(boolean compressedOops, long totalOvercount, double correctionRatio) {
        static final OopsConfig DISABLED = new OopsConfig(false, 0, 1.0);
    }

    private OopsConfig resolveOopsConfig(Heap heap) {
        Optional<HeapDumpConfig> configOpt = getHeapDumpConfig();
        boolean compressedOops = configOpt.map(HeapDumpConfig::compressedOops).orElse(false);
        long totalOvercount = configOpt.map(HeapDumpConfig::totalOvercount).orElse(0L);
        if (!compressedOops) {
            return OopsConfig.DISABLED;
        }
        double correctionRatio = CompressedOopsCorrector.computeCorrectionRatio(
                heap.getSummary().getTotalLiveBytes(), totalOvercount);
        return new OopsConfig(true, totalOvercount, correctionRatio);
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
        try {
            FileSystemUtils.removeDirectory(heapDumpAnalysisPath);
            LOG.info("Deleted corrupted heap dump analysis folder: profileId={} path={}", profileInfo.id(), heapDumpAnalysisPath);
        } catch (RuntimeException e) {
            LOG.warn("Failed to delete corrupted heap dump analysis folder: profileId={} path={}", profileInfo.id(), heapDumpAnalysisPath, e);
        }
    }

    @Override
    public boolean stringAnalysisExists() {
        return Files.exists(heapDumpAnalysisPath.resolve(STRING_ANALYSIS_FILE));
    }

    @Override
    public StringAnalysisReport getStringAnalysis() {
        return readJsonFile(STRING_ANALYSIS_FILE, StringAnalysisReport.class).orElse(null);
    }

    @Override
    public void runStringAnalysis(int topN) {
        getHeap().ifPresent(heap -> {
            OopsConfig oops = resolveOopsConfig(heap);
            // Analyze heap for string deduplication
            StringAnalysisReport heapAnalysis = stringAnalyzer.analyze(heap, topN, oops.compressedOops);

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

            writeJsonFile(STRING_ANALYSIS_FILE, report, "String analysis");
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
    public boolean threadAnalysisExists() {
        return Files.exists(heapDumpAnalysisPath.resolve(THREAD_ANALYSIS_FILE));
    }

    @Override
    public ThreadAnalysisReport getThreadAnalysis() {
        return readJsonFile(THREAD_ANALYSIS_FILE, ThreadAnalysisReport.class).orElse(null);
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

            writeJsonFile(THREAD_ANALYSIS_FILE, report, "Thread analysis");
        });
    }

    @Override
    public InstanceDetail getInstanceDetail(long objectId, boolean includeRetainedSize) {
        return getHeap().map(heap -> {
            OopsConfig oops = resolveOopsConfig(heap);
            return instanceDetailAnalyzer.analyze(heap, objectId, includeRetainedSize,
                    oops.compressedOops, oops.correctionRatio);
        }).orElse(null);
    }

    @Override
    public InstanceTreeResponse getReferrers(long objectId, int limit, int offset) {
        return getHeap().map(heap -> {
            OopsConfig oops = resolveOopsConfig(heap);
            return instanceTreeAnalyzer.getReferrers(heap, objectId, limit, offset, oops.compressedOops);
        }).orElse(InstanceTreeResponse.notFound());
    }

    @Override
    public InstanceTreeResponse getReachables(long objectId, int limit, int offset) {
        return getHeap().map(heap -> {
            OopsConfig oops = resolveOopsConfig(heap);
            return instanceTreeAnalyzer.getReachables(heap, objectId, limit, offset, oops.compressedOops);
        }).orElse(InstanceTreeResponse.notFound());
    }

    // --- Path to GC Root ---

    @Override
    public List<GCRootPath> getPathsToGCRoot(long objectId, boolean excludeWeakRefs, int maxPaths) {
        return getHeap().map(heap -> {
            OopsConfig oops = resolveOopsConfig(heap);
            return pathToGCRootAnalyzer.findPaths(heap, objectId, excludeWeakRefs, maxPaths, oops.compressedOops, oops.correctionRatio);
        }).orElse(List.of());
    }

    // --- Heap Dump Config ---

    @Override
    public HeapDumpConfig resolveAndStoreCompressedOops(Boolean manualOverride) {
        boolean compressedOops;
        String source;

        if (manualOverride != null) {
            compressedOops = manualOverride;
            source = "MANUAL";
        } else {
            Optional<Boolean> jfrValue = detectCompressedOopsFromJfr();
            if (jfrValue.isPresent()) {
                compressedOops = jfrValue.get();
                source = "JFR";
            } else {
                compressedOops = inferCompressedOopsFromHeap();
                source = "INFERRED";
            }
        }

        long totalOvercount = 0;
        if (compressedOops) {
            totalOvercount = getHeap()
                    .map(CompressedOopsCorrector::computeTotalOvercount)
                    .orElse(0L);
            LOG.info("Total overcount computed: totalOvercount={} profileId={}", totalOvercount, profileInfo.id());
        }

        HeapDumpConfig config = new HeapDumpConfig(compressedOops, source, totalOvercount);
        writeJsonFile(HEAP_DUMP_CONFIG_FILE, config, "Heap dump config");
        LOG.info("Compressed oops resolved: compressedOops={} source={} profileId={}", compressedOops, source, profileInfo.id());
        return config;
    }

    @Override
    public Optional<HeapDumpConfig> getHeapDumpConfig() {
        return readJsonFile(HEAP_DUMP_CONFIG_FILE, HeapDumpConfig.class);
    }

    private boolean inferCompressedOopsFromHeap() {
        return getHeap().map(heap -> {
            try {
                java.util.Properties props = heap.getSystemProperties();
                String archDataModel = props.getProperty("sun.arch.data.model", "");
                if ("64".equals(archDataModel)) {
                    long totalLiveBytes = heap.getSummary().getTotalLiveBytes();
                    return totalLiveBytes < COMPRESSED_OOPS_MAX_HEAP;
                }
            } catch (Exception e) {
                LOG.debug("Could not infer compressed oops from heap properties: {}", e.getMessage());
            }
            return false;
        }).orElse(false);
    }

    private Optional<Boolean> detectCompressedOopsFromJfr() {
        try {
            return eventRepository.latestJsonFields(Type.GC_HEAP_CONFIGURATION)
                    .map(fields -> Json.treeToValue(fields, GCHeapConfiguration.class))
                    .map(GCHeapConfiguration::usesCompressedOops);
        } catch (Exception e) {
            LOG.debug("Could not detect compressed oops from JFR events: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Dominator Tree ---

    @Override
    public DominatorTreeResponse getDominatorTreeRoots(int limit) {
        Optional<HeapDumpConfig> configOpt = getHeapDumpConfig();
        boolean compressedOops = configOpt.map(HeapDumpConfig::compressedOops).orElse(false);
        long totalOvercount = configOpt.map(HeapDumpConfig::totalOvercount).orElse(0L);
        return getHeap()
                .map(heap -> dominatorTreeAnalyzer.getRoots(heap, limit, compressedOops, totalOvercount))
                .orElse(new DominatorTreeResponse(List.of(), 0, false, false));
    }

    @Override
    public DominatorTreeResponse getDominatorTreeChildren(long objectId, int limit) {
        Optional<HeapDumpConfig> configOpt = getHeapDumpConfig();
        boolean compressedOops = configOpt.map(HeapDumpConfig::compressedOops).orElse(false);
        long totalOvercount = configOpt.map(HeapDumpConfig::totalOvercount).orElse(0L);
        return getHeap()
                .map(heap -> dominatorTreeAnalyzer.getChildren(heap, objectId, limit, compressedOops, totalOvercount))
                .orElse(new DominatorTreeResponse(List.of(), 0, false, false));
    }

    // --- Collection Analysis ---

    @Override
    public boolean collectionAnalysisExists() {
        return Files.exists(heapDumpAnalysisPath.resolve(COLLECTION_ANALYSIS_FILE));
    }

    @Override
    public CollectionAnalysisReport getCollectionAnalysis() {
        return readJsonFile(COLLECTION_ANALYSIS_FILE, CollectionAnalysisReport.class).orElse(null);
    }

    @Override
    public void runCollectionAnalysis() {
        getHeap().ifPresent(heap -> {
            OopsConfig oops = resolveOopsConfig(heap);
            CollectionAnalysisReport report = collectionAnalyzer.analyze(heap, oops.compressedOops);
            writeJsonFile(COLLECTION_ANALYSIS_FILE, report, "Collection analysis");
        });
    }

    // --- Class Instance Browser ---

    @Override
    public ClassInstancesResponse getClassInstances(String className, int limit, int offset, boolean includeRetainedSize) {
        return getHeap().map(heap -> {
            OopsConfig oops = resolveOopsConfig(heap);
            return classInstanceBrowserAnalyzer.browse(heap, className, limit, offset, includeRetainedSize,
                    oops.compressedOops, oops.correctionRatio);
        }).orElse(new ClassInstancesResponse(className, 0, List.of(), false));
    }

    // --- Leak Suspects ---

    @Override
    public boolean leakSuspectsExists() {
        return Files.exists(heapDumpAnalysisPath.resolve(LEAK_SUSPECTS_FILE));
    }

    @Override
    public LeakSuspectsReport getLeakSuspects() {
        return readJsonFile(LEAK_SUSPECTS_FILE, LeakSuspectsReport.class).orElse(null);
    }

    @Override
    public void runLeakSuspects() {
        getHeap().ifPresent(heap -> {
            OopsConfig oops = resolveOopsConfig(heap);
            LeakSuspectsReport report = leakSuspectsAnalyzer.analyze(
                    heap, oops.compressedOops, oops.correctionRatio, oops.totalOvercount);
            writeJsonFile(LEAK_SUSPECTS_FILE, report, "Leak suspects");
        });
    }

    // --- Biggest Objects ---

    @Override
    public boolean biggestObjectsExists() {
        return Files.exists(heapDumpAnalysisPath.resolve(BIGGEST_OBJECTS_FILE));
    }

    @Override
    public BiggestObjectsReport getBiggestObjects() {
        return readJsonFile(BIGGEST_OBJECTS_FILE, BiggestObjectsReport.class).orElse(null);
    }

    @Override
    public void runBiggestObjects(int topN) {
        getHeap().ifPresent(heap -> {
            OopsConfig oops = resolveOopsConfig(heap);
            DominatorTreeResponse response = dominatorTreeAnalyzer.getRoots(heap, topN, oops.compressedOops, oops.totalOvercount);

            List<BiggestObjectEntry> entries = response.nodes().stream()
                    .map(node -> new BiggestObjectEntry(
                            node.className(),
                            node.shallowSize(),
                            node.retainedSize(),
                            node.objectId()))
                    .toList();

            long totalRetained = entries.stream().mapToLong(BiggestObjectEntry::retainedSize).sum();
            long totalHeapSize = heap.getSummary().getTotalLiveBytes();

            BiggestObjectsReport report = new BiggestObjectsReport(totalHeapSize, totalRetained, entries);
            writeJsonFile(BIGGEST_OBJECTS_FILE, report, "Biggest objects");
        });
    }

    // --- JSON I/O helpers ---

    private <T> Optional<T> readJsonFile(String fileName, Class<T> type) {
        Path filePath = heapDumpAnalysisPath.resolve(fileName);
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            T report = OBJECT_MAPPER.readValue(filePath.toFile(), type);
            return Optional.of(report);
        } catch (IOException e) {
            LOG.error("Failed to read analysis file: path={}", filePath, e);
            return Optional.empty();
        }
    }

    private void writeJsonFile(String fileName, Object report, String analysisName) {
        try {
            Files.createDirectories(heapDumpAnalysisPath);
            Path filePath = heapDumpAnalysisPath.resolve(fileName);
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(filePath.toFile(), report);
            LOG.info("{} saved: path={}", analysisName, filePath);
        } catch (IOException e) {
            LOG.error("Failed to save {}: path={}", analysisName, heapDumpAnalysisPath, e);
            throw new RuntimeException("Failed to save " + analysisName + ": " + e.getMessage(), e);
        }
    }

    private void deleteJsonFile(String fileName, String analysisName) {
        Path filePath = heapDumpAnalysisPath.resolve(fileName);
        if (Files.exists(filePath)) {
            try {
                Files.delete(filePath);
                LOG.info("{} deleted: path={}", analysisName, filePath);
            } catch (IOException e) {
                LOG.error("Failed to delete {}: path={}", analysisName, filePath, e);
            }
        }
    }
}
