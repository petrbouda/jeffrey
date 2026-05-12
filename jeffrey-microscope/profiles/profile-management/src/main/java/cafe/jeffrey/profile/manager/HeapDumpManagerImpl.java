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

package cafe.jeffrey.profile.manager;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassHistogramAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassInstanceBrowserAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.BiggestCollectionsAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassLoaderAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassLoaderLeakChainAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.CollectionAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ConsumerReportAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.DominatorTreeAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.GcRootAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.HeapSummaryAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.InstanceDetailAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.InstanceTreeAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.LeakSuspectsAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.PathToGCRootAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.StringAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ThreadAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ThreadStackAnalyzer;
import cafe.jeffrey.profile.heapdump.model.BiggestCollectionsReport;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectEntry;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectsReport;
import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstancesResponse;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakChain;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.ConsumerReport;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.GCRootSummary;
import cafe.jeffrey.profile.heapdump.model.HeapDumpConfig;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.profile.heapdump.model.InitPipelineResult;
import cafe.jeffrey.profile.heapdump.model.InstanceDetail;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeRequest;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeResponse;
import cafe.jeffrey.profile.heapdump.model.JvmStringFlag;
import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.profile.heapdump.model.OQLQueryRequest;
import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.SortBy;
import cafe.jeffrey.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.ThreadAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.ThreadStackFrame;
import cafe.jeffrey.profile.heapdump.oql.OqlEngine;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan;
import cafe.jeffrey.profile.heapdump.oql.parser.OqlParseException;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpSession;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;
import cafe.jeffrey.profile.common.event.GCHeapConfiguration;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.provider.profile.api.JvmFlag;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.repository.FileExtensions;

import cafe.jeffrey.shared.common.measure.Measuring;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of HeapDumpManager on the native parser path
 * ({@link HeapDumpSession} + {@code analyzer.heapview}). Each method opens
 * a short-lived session over the heap dump's {@code .idx.duckdb} sibling,
 * runs the corresponding analyzer, and lets try-with-resources close the
 * session afterwards.
 *
 * <p>Compressed-oops correction is not applied on this path; shallow sizes
 * are computed with the parser's fixed 16-byte header constant. The
 * {@link #resolveAndStoreCompressedOops(Boolean)} method still records the
 * detected setting so the frontend can display it, but the recorded
 * overcount is always 0 and no correction ratio is applied to any reported
 * size.
 */
public class HeapDumpManagerImpl implements HeapDumpManager {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpManagerImpl.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STRING_ANALYSIS_FILE = "string-analysis.json";
    private static final String THREAD_ANALYSIS_FILE = "thread-analysis.json";
    private static final String COLLECTION_ANALYSIS_FILE = "collection-analysis.json";
    private static final String BIGGEST_OBJECTS_FILE = "biggest-objects.json";
    private static final String LEAK_SUSPECTS_FILE = "leak-suspects.json";
    private static final String BIGGEST_COLLECTIONS_FILE = "biggest-collections.json";
    private static final String CLASSLOADER_ANALYSIS_FILE = "classloader-analysis.json";
    private static final String CONSUMER_REPORT_FILE = "consumer-report.json";
    private static final String HEAP_DUMP_CONFIG_FILE = "heap-dump-config.json";
    private static final String INIT_PIPELINE_RESULT_FILE = "init-pipeline-result.json";

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
    private final AdditionalFilesManager additionalFilesManager;
    private final ProfileEventRepository eventRepository;
    private final Clock clock;
    private final Path heapDumpAnalysisPath;
    private final OqlEngine oqlEngine;

    public HeapDumpManagerImpl(
            ProfileInfo profileInfo,
            AdditionalFilesManager additionalFilesManager,
            ProfileEventRepository eventRepository,
            Clock clock,
            OqlEngine oqlEngine) {

        this.profileInfo = profileInfo;
        this.additionalFilesManager = additionalFilesManager;
        this.eventRepository = eventRepository;
        this.clock = clock;
        this.heapDumpAnalysisPath = additionalFilesManager.getHeapDumpAnalysisPath();
        this.oqlEngine = oqlEngine;
    }

    // --- Lifecycle helpers ------------------------------------------------

    @FunctionalInterface
    private interface SessionWork<R> {
        R apply(HeapDumpSession session) throws SQLException, IOException;
    }

    private <R> Optional<R> withSession(SessionWork<R> work) {
        Optional<Path> heapPath = additionalFilesManager.getHeapDumpPath();
        if (heapPath.isEmpty()) {
            LOG.debug("No heap dump available: profileId={}", profileInfo.id());
            return Optional.empty();
        }
        try (HeapDumpSession session = HeapDumpSession.openOrBuild(heapPath.get(), clock)) {
            return Optional.ofNullable(work.apply(session));
        } catch (IOException | SQLException e) {
            LOG.warn("Heap dump operation failed: profileId={} path={} error={}",
                    profileInfo.id(), heapPath.get(), e.getMessage());
            throw new RuntimeException("Heap dump operation failed: " + e.getMessage(), e);
        }
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
        Path indexPath = HeapDumpIndexPaths.indexFor(heapPath.get());
        if (!Files.exists(indexPath)) {
            return false;
        }
        // Index is stale if its mtime is older than the source dump.
        try {
            long hprofMtime = Files.getLastModifiedTime(heapPath.get()).toMillis();
            long indexMtime = Files.getLastModifiedTime(indexPath).toMillis();
            return indexMtime >= hprofMtime;
        } catch (IOException e) {
            return false;
        }
    }

    // --- Summary + Histogram ---------------------------------------------

    @Override
    public HeapSummary getSummary() {
        return withSession(session -> HeapSummaryAnalyzer.analyze(session.view())).orElse(null);
    }

    @Override
    public List<ClassHistogramEntry> getClassHistogram(int topN) {
        return getClassHistogram(topN, SortBy.SIZE);
    }

    @Override
    public List<ClassHistogramEntry> getClassHistogram(int topN, SortBy sortBy) {
        return withSession(session -> ClassHistogramAnalyzer.analyze(session.view(), topN, sortBy))
                .orElse(List.of());
    }

    // --- OQL execution against the heap-dump-index DuckDB ---------------

    private static final int MAX_QUERY_LIMIT = 100;

    @Override
    public OQLQueryResult executeQuery(OQLQueryRequest request) {
        String query = request.query() == null ? "" : request.query().trim();
        if (query.isBlank()) {
            return OQLQueryResult.error("Query is empty", 0);
        }

        int effectiveLimit = Math.clamp(request.limit(), 1, MAX_QUERY_LIMIT);

        OqlStatement stmt;
        try {
            stmt = oqlEngine.parse(query);
        } catch (OqlParseException e) {
            return OQLQueryResult.error("Parse error at " + e.location() + ": " + e.getMessage(), 0);
        }

        ExecutionPlan plan;
        try {
            plan = oqlEngine.compile(stmt);
        } catch (RuntimeException e) {
            return OQLQueryResult.error("Compile error: " + e.getMessage(), 0);
        }

        var elapsed = Measuring.s(() -> withSession(session -> {
            if (plan.needsDominatorTree() || request.includeRetainedSize()) {
                session.buildDominatorTreeIfNeeded();
            }
            try {
                return oqlEngine.execute(plan, session.view(), effectiveLimit);
            } catch (SQLException e) {
                LOG.warn("OQL execution failed: query={} error={}", query, e.getMessage());
                return OQLQueryResult.error("Query failed: " + e.getMessage(), 0);
            }
        }).orElseGet(() -> OQLQueryResult.error("Heap dump not available for this profile", 0)));

        OQLQueryResult r = elapsed.entity();
        return new OQLQueryResult(
                r.results(), r.totalCount(), r.hasMore(),
                elapsed.duration().toMillis(), r.errorMessage());
    }

    // --- Threads ---------------------------------------------------------

    @Override
    public List<HeapThreadInfo> getThreads() {
        return getThreads(false);
    }

    @Override
    public List<HeapThreadInfo> getThreads(boolean includeRetainedSize) {
        return withSession(session -> {
            if (includeRetainedSize) {
                session.buildDominatorTreeIfNeeded();
            }
            return ThreadAnalyzer.analyze(session.view());
        }).orElse(List.of());
    }

    @Override
    public List<ThreadStackFrame> getThreadStack(long threadObjectId) {
        return withSession(session ->
                ThreadStackAnalyzer.getStack(session.view(), threadObjectId))
                .orElse(List.of());
    }

    @Override
    public GCRootSummary getGCRootSummary() {
        return withSession(session -> GcRootAnalyzer.analyze(session.view()))
                .orElse(GCRootSummary.EMPTY);
    }

    // --- Heap lifecycle / cleanup ----------------------------------------

    @Override
    public void unloadHeap() {
        // The native parser path doesn't hold long-lived heap state; every
        // analyzer call opens and closes its own short-lived session.
    }

    @Override
    public void deleteCache() {
        Optional<Path> heapPath = additionalFilesManager.getHeapDumpPath();
        heapPath.ifPresent(path -> {
            Path indexPath = HeapDumpIndexPaths.indexFor(path);
            try {
                Files.deleteIfExists(indexPath);
            } catch (IOException e) {
                LOG.error("Failed to delete heap dump index: profileId={} path={}",
                        profileInfo.id(), indexPath, e);
            }
        });

        // Delete all pre-computed analysis files.
        deleteJsonFile(STRING_ANALYSIS_FILE, "String analysis");
        deleteJsonFile(THREAD_ANALYSIS_FILE, "Thread analysis");
        deleteJsonFile(COLLECTION_ANALYSIS_FILE, "Collection analysis");
        deleteJsonFile(LEAK_SUSPECTS_FILE, "Leak suspects");
        deleteJsonFile(BIGGEST_OBJECTS_FILE, "Biggest objects");
        deleteJsonFile(BIGGEST_COLLECTIONS_FILE, "Biggest collections");
        deleteJsonFile(CLASSLOADER_ANALYSIS_FILE, "Class loader analysis");
        deleteJsonFile(CONSUMER_REPORT_FILE, "Consumer report");
        deleteJsonFile(HEAP_DUMP_CONFIG_FILE, "Heap dump config");
        deleteJsonFile(INIT_PIPELINE_RESULT_FILE, "Init pipeline result");
    }

    @Override
    public void deleteHeapDump() {
        Optional<Path> heapPath = additionalFilesManager.getHeapDumpPath();
        if (heapPath.isEmpty()) {
            return;
        }

        Path path = heapPath.get();
        String fileName = path.getFileName().toString().toLowerCase();

        Path hprofPath;
        Path gzPath = null;
        if (fileName.endsWith(FileExtensions.HPROF_GZ)) {
            gzPath = path;
            String decompressedName = path.getFileName().toString();
            decompressedName = decompressedName.substring(0, decompressedName.length() - 3);
            hprofPath = path.resolveSibling(decompressedName);
        } else {
            hprofPath = path;
            Path potentialGz = path.resolveSibling(path.getFileName() + ".gz");
            if (Files.exists(potentialGz)) {
                gzPath = potentialGz;
            }
        }

        // Delete the .idx.duckdb sibling.
        Path indexPath = HeapDumpIndexPaths.indexFor(hprofPath);
        try {
            Files.deleteIfExists(indexPath);
        } catch (IOException e) {
            LOG.error("Failed to delete heap dump index: profileId={} path={}",
                    profileInfo.id(), indexPath, e);
        }

        // Delete .hprof file.
        if (Files.exists(hprofPath)) {
            try {
                Files.delete(hprofPath);
            } catch (IOException e) {
                LOG.error("Failed to delete heap dump: profileId={} path={}",
                        profileInfo.id(), hprofPath, e);
            }
        }

        // Delete .hprof.gz file.
        if (gzPath != null && Files.exists(gzPath)) {
            try {
                Files.delete(gzPath);
            } catch (IOException e) {
                LOG.error("Failed to delete compressed heap dump: profileId={} path={}",
                        profileInfo.id(), gzPath, e);
            }
        }
    }

    // --- Upload + sanitize ----------------------------------------------

    @Override
    public void uploadHeapDump(InputStream inputStream, String filename) {
        // Validate filename extension.
        String lowerFilename = filename.toLowerCase();
        if (!lowerFilename.endsWith("." + FileExtensions.HPROF) &&
                !lowerFilename.endsWith("." + FileExtensions.HPROF_GZ)) {
            throw new IllegalArgumentException("Invalid file type. Only .hprof and .hprof.gz files are supported.");
        }

        // Clean up any existing files / index before uploading new heap dump.
        try {
            FileSystemUtils.removeDirectory(heapDumpAnalysisPath);
        } catch (RuntimeException e) {
            LOG.warn("Failed to clean up existing heap dump directory: profileId={} path={}",
                    profileInfo.id(), heapDumpAnalysisPath, e);
        }

        Path targetPath = heapDumpAnalysisPath.resolve(filename);
        try {
            Files.createDirectories(heapDumpAnalysisPath);
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Heap dump uploaded: profileId={} path={}", profileInfo.id(), targetPath);
        } catch (IOException e) {
            LOG.error("Failed to upload heap dump: profileId={} filename={}",
                    profileInfo.id(), filename, e);
            throw new RuntimeException("Failed to upload heap dump: " + e.getMessage(), e);
        }
    }

    @Override
    public void sanitizeHeapDump() {
        // The native parser is fault-tolerant by design — framing recovery is
        // applied inline during indexing rather than as a separate pre-pass.
        // Keep this as a no-op so any frontend that still calls /sanitize gets
        // a clean response.
        LOG.debug("sanitizeHeapDump is a no-op on the native parser path: profileId={}",
                profileInfo.id());
    }

    // --- String analysis -------------------------------------------------

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
        withSession(session -> {
            StringAnalysisReport heapAnalysis = StringAnalyzer.analyze(session.view(), topN);

            // Get JVM flags related to strings from JFR events.
            List<JvmStringFlag> jvmFlags = getStringRelatedJvmFlags();

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
            return null;
        });
    }

    private List<JvmStringFlag> getStringRelatedJvmFlags() {
        List<JvmFlag> flags = eventRepository.getStringRelatedFlags();
        return flags.stream()
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

    // --- Thread analysis -------------------------------------------------

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
        withSession(session -> {
            // Retained sizes need the dominator tree; the analyzer reads
            // retained_size when present.
            session.buildDominatorTreeIfNeeded();
            List<HeapThreadInfo> threads = ThreadAnalyzer.analyze(session.view());

            int daemonCount = (int) threads.stream().filter(HeapThreadInfo::daemon).count();
            int userCount = threads.size() - daemonCount;
            long totalRetained = threads.stream()
                    .mapToLong(t -> t.retainedSize() != null ? t.retainedSize() : 0L)
                    .sum();

            ThreadAnalysisReport report = new ThreadAnalysisReport(
                    threads.size(), daemonCount, userCount, totalRetained, threads);
            writeJsonFile(THREAD_ANALYSIS_FILE, report, "Thread analysis");
            return null;
        });
    }

    // --- Instance browsing -----------------------------------------------

    @Override
    public InstanceDetail getInstanceDetail(long objectId, boolean includeRetainedSize) {
        return withSession(session -> {
            if (includeRetainedSize) {
                session.buildDominatorTreeIfNeeded();
            }
            return InstanceDetailAnalyzer.analyze(session.view(), objectId).orElse(null);
        }).orElse(null);
    }

    @Override
    public InstanceTreeResponse getReferrers(long objectId, int limit, int offset) {
        return withSession(session -> InstanceTreeAnalyzer.analyze(
                session.view(),
                InstanceTreeRequest.referrers(objectId, limit, offset)))
                .orElse(InstanceTreeResponse.notFound());
    }

    @Override
    public InstanceTreeResponse getReachables(long objectId, int limit, int offset) {
        return withSession(session -> InstanceTreeAnalyzer.analyze(
                session.view(),
                InstanceTreeRequest.reachables(objectId, limit, offset)))
                .orElse(InstanceTreeResponse.notFound());
    }

    // --- Path to GC Root -------------------------------------------------

    @Override
    public List<GCRootPath> getPathsToGCRoot(long objectId, boolean excludeWeakRefs, int maxPaths) {
        return withSession(session -> PathToGCRootAnalyzer.findPaths(
                session.view(), objectId, excludeWeakRefs, maxPaths))
                .orElse(List.of());
    }

    // --- Heap Dump Config (compressed oops detection only — correction is not applied) ----

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
                Optional<Boolean> inferred = inferCompressedOopsFromHeap();
                if (inferred.isPresent()) {
                    compressedOops = inferred.get();
                    source = "INFERRED";
                } else {
                    compressedOops = false;
                    source = "DEFAULTED";
                }
            }
        }

        // totalOvercount is always 0 — the native parser doesn't compute it
        // (CUTOVER.md §6: compressed-oops correction is not applied).
        HeapDumpConfig config = new HeapDumpConfig(compressedOops, source, 0L);
        writeJsonFile(HEAP_DUMP_CONFIG_FILE, config, "Heap dump config");
        LOG.info("Compressed oops resolved: compressedOops={} source={} profileId={}",
                compressedOops, source, profileInfo.id());
        return config;
    }

    @Override
    public Optional<HeapDumpConfig> getHeapDumpConfig() {
        return readJsonFile(HEAP_DUMP_CONFIG_FILE, HeapDumpConfig.class);
    }

    // --- Init pipeline result ---

    @Override
    public boolean initPipelineResultExists() {
        return Files.exists(heapDumpAnalysisPath.resolve(INIT_PIPELINE_RESULT_FILE));
    }

    @Override
    public Optional<InitPipelineResult> getInitPipelineResult() {
        return readJsonFile(INIT_PIPELINE_RESULT_FILE, InitPipelineResult.class);
    }

    @Override
    public void storeInitPipelineResult(InitPipelineResult result) {
        writeJsonFile(INIT_PIPELINE_RESULT_FILE, result, "Init pipeline result");
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

    /**
     * Reads the compressed-oops flag the parser baked into the index at build
     * time. The parser uses the same JVM-default heuristic (64-bit dump with a
     * heap under the 32 GiB pointer-compression limit), so this is a pure
     * pass-through; the value drives per-instance shallow-size accounting and
     * is recorded here as the {@code INFERRED} source for the heap-dump
     * config. Returns empty when no view is available so {@code DEFAULTED}
     * wins downstream.
     */
    private Optional<Boolean> inferCompressedOopsFromHeap() {
        return withSession(session -> session.view().metadata().compressedOops());
    }

    // --- Dominator Tree --------------------------------------------------

    @Override
    public DominatorTreeResponse getDominatorTreeRoots(int limit) {
        return withSession(session -> {
            session.buildDominatorTreeIfNeeded();
            return DominatorTreeAnalyzer.children(session.view(), 0L, limit);
        }).orElse(new DominatorTreeResponse(List.of(), 0, false, false));
    }

    @Override
    public DominatorTreeResponse getDominatorTreeChildren(long objectId, int offset, int limit) {
        // The native DominatorTreeAnalyzer does not currently support offset-based
        // pagination; offset is ignored. The frontend uses lazy expansion, so the
        // first `limit` children is what each call needs.
        return withSession(session -> {
            session.buildDominatorTreeIfNeeded();
            return DominatorTreeAnalyzer.children(session.view(), objectId, limit);
        }).orElse(new DominatorTreeResponse(List.of(), 0, false, false));
    }

    // --- Collection Analysis ---------------------------------------------

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
        withSession(session -> {
            CollectionAnalysisReport report = CollectionAnalyzer.analyze(session.view());
            writeJsonFile(COLLECTION_ANALYSIS_FILE, report, "Collection analysis");
            return null;
        });
    }

    // --- Class Instance Browser ------------------------------------------

    @Override
    public ClassInstancesResponse getClassInstances(
            String className, int limit, int offset, boolean includeRetainedSize) {
        return withSession(session -> {
            if (includeRetainedSize) {
                session.buildDominatorTreeIfNeeded();
            }
            HeapView view = session.view();
            List<JavaClassRow> matches = view.findClassesByName(className);
            if (matches.isEmpty()) {
                return new ClassInstancesResponse(className, 0, List.of(), false);
            }
            return ClassInstanceBrowserAnalyzer.browse(view, matches.get(0).classId(), offset, limit);
        }).orElse(new ClassInstancesResponse(className, 0, List.of(), false));
    }

    // --- Leak Suspects ---------------------------------------------------

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
        withSession(session -> {
            session.buildDominatorTreeIfNeeded();
            LeakSuspectsReport report = LeakSuspectsAnalyzer.analyze(session.view());
            writeJsonFile(LEAK_SUSPECTS_FILE, report, "Leak suspects");
            return null;
        });
    }

    // --- Biggest Objects -------------------------------------------------

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
        withSession(session -> {
            session.buildDominatorTreeIfNeeded();
            HeapView view = session.view();
            DominatorTreeResponse response = DominatorTreeAnalyzer.children(view, 0L, topN);

            List<BiggestObjectEntry> entries = response.nodes().stream()
                    .map(node -> new BiggestObjectEntry(
                            node.className(),
                            node.shallowSize(),
                            node.retainedSize(),
                            node.objectId()))
                    .toList();

            long totalRetained = entries.stream().mapToLong(BiggestObjectEntry::retainedSize).sum();
            long totalHeapSize;
            try {
                totalHeapSize = view.totalShallowSize();
            } catch (SQLException e) {
                totalHeapSize = 0L;
            }
            BiggestObjectsReport report = new BiggestObjectsReport(totalHeapSize, totalRetained, entries);
            writeJsonFile(BIGGEST_OBJECTS_FILE, report, "Biggest objects");
            return null;
        });
    }

    // --- Biggest Collections (not yet migrated — empty report) -----------

    @Override
    public boolean biggestCollectionsExists() {
        return Files.exists(heapDumpAnalysisPath.resolve(BIGGEST_COLLECTIONS_FILE));
    }

    @Override
    public BiggestCollectionsReport getBiggestCollections() {
        return readJsonFile(BIGGEST_COLLECTIONS_FILE, BiggestCollectionsReport.class).orElse(null);
    }

    @Override
    public void runBiggestCollections(int topN) {
        withSession(session -> {
            session.buildDominatorTreeIfNeeded();
            BiggestCollectionsReport report =
                    BiggestCollectionsAnalyzer.analyze(session.view(), topN);
            writeJsonFile(BIGGEST_COLLECTIONS_FILE, report, "Biggest collections");
            return null;
        });
    }

    // --- Class Loader Analysis (base + leak chains) ----------------------

    @Override
    public boolean classLoaderAnalysisExists() {
        return Files.exists(heapDumpAnalysisPath.resolve(CLASSLOADER_ANALYSIS_FILE));
    }

    @Override
    public ClassLoaderReport getClassLoaderAnalysis() {
        return readJsonFile(CLASSLOADER_ANALYSIS_FILE, ClassLoaderReport.class).orElse(null);
    }

    @Override
    public void runClassLoaderAnalysis() {
        withSession(session -> {
            session.buildDominatorTreeIfNeeded();
            HeapView view = session.view();
            ClassLoaderReport baseReport = ClassLoaderAnalyzer.analyze(view);
            List<ClassLoaderLeakChain> leakChains = ClassLoaderLeakChainAnalyzer.analyze(view);

            ClassLoaderReport report = new ClassLoaderReport(
                    baseReport.totalClassLoaders(),
                    baseReport.totalClasses(),
                    baseReport.duplicateClassCount(),
                    baseReport.classLoaders(),
                    baseReport.duplicateClasses(),
                    leakChains);
            writeJsonFile(CLASSLOADER_ANALYSIS_FILE, report, "Class loader analysis");
            return null;
        });
    }

    // --- Consumer Report ------------------------------------------------

    @Override
    public boolean consumerReportExists() {
        return Files.exists(heapDumpAnalysisPath.resolve(CONSUMER_REPORT_FILE));
    }

    @Override
    public ConsumerReport getConsumerReport() {
        return readJsonFile(CONSUMER_REPORT_FILE, ConsumerReport.class).orElse(null);
    }

    @Override
    public void runConsumerReport() {
        withSession(session -> {
            session.buildDominatorTreeIfNeeded();
            ConsumerReport report = ConsumerReportAnalyzer.analyze(session.view());
            writeJsonFile(CONSUMER_REPORT_FILE, report, "Consumer report");
            return null;
        });
    }

    // --- JSON I/O helpers -----------------------------------------------

    private <T> Optional<T> readJsonFile(String fileName, Class<T> type) {
        Path filePath = heapDumpAnalysisPath.resolve(fileName);
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            T report = OBJECT_MAPPER.readValue(filePath.toFile(), type);
            return Optional.of(report);
        } catch (JacksonException e) {
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
        } catch (IOException | JacksonException e) {
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
