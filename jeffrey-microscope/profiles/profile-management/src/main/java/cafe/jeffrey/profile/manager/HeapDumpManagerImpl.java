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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassHistogramAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassInstanceBrowserAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.DominatorTreeAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.GcRootAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.HeapSummaryAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.InstanceDetailAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.InstanceTreeAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.PathToGCRootAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ThreadAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ThreadStackAnalyzer;
import cafe.jeffrey.profile.heapdump.model.BiggestCollectionsReport;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectsReport;
import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstancesResponse;
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
import cafe.jeffrey.profile.heapdump.model.InitializeResult;
import cafe.jeffrey.profile.heapdump.model.InstanceDetail;
import cafe.jeffrey.profile.heapdump.model.InstanceSortBy;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeRequest;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeResponse;
import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.profile.heapdump.model.OQLQueryRequest;
import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.SortBy;
import cafe.jeffrey.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.SubPhaseTiming;
import cafe.jeffrey.profile.heapdump.model.ThreadAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.ThreadStackFrame;
import cafe.jeffrey.profile.heapdump.oql.OqlEngine;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.OqlCompileOptions;
import cafe.jeffrey.profile.heapdump.oql.parser.OqlParseException;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpSession;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;
import cafe.jeffrey.profile.manager.heapdump.CachedAnalysisRunner;
import cafe.jeffrey.profile.manager.heapdump.CompressedOopsResolver;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpReportStore;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpSessionTemplate;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpUploadService;
import cafe.jeffrey.profile.manager.heapdump.JvmStringFlagsProvider;
import cafe.jeffrey.profile.manager.heapdump.analysis.BiggestCollectionsAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.BiggestObjectsAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.ClassLoaderHeapAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.CollectionHeapAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.ConsumerReportAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.LeakSuspectsAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.StringHeapAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.ThreadHeapAnalysis;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.shared.common.measure.Measuring;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
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

    private static final String INIT_PIPELINE_RESULT_FILE = "init-pipeline-result.json";

    private static final String INIT_PIPELINE_RESULT_DISPLAY_NAME = "Init pipeline result";

    private static final int MAX_QUERY_LIMIT = 100;

    private static final long DOMINATOR_ROOT_ID = 0L;

    private static final DominatorTreeResponse EMPTY_DOMINATOR_TREE =
            new DominatorTreeResponse(List.of(), 0, false, false);

    private final OqlEngine oqlEngine;
    private final HeapDumpReportStore reports;
    private final HeapDumpSessionTemplate sessions;
    private final CachedAnalysisRunner runner;
    private final JvmStringFlagsProvider jvmStringFlagsProvider;
    private final CompressedOopsResolver compressedOopsResolver;
    private final HeapDumpUploadService uploadService;

    public HeapDumpManagerImpl(
            ProfileInfo profileInfo,
            AdditionalFilesManager additionalFilesManager,
            ProfileEventRepository eventRepository,
            Clock clock,
            OqlEngine oqlEngine) {

        this.oqlEngine = oqlEngine;
        this.reports = new HeapDumpReportStore(additionalFilesManager.getHeapDumpAnalysisPath());
        this.sessions = new HeapDumpSessionTemplate(profileInfo, additionalFilesManager, clock);
        this.runner = new CachedAnalysisRunner(sessions, reports);
        this.jvmStringFlagsProvider = new JvmStringFlagsProvider(eventRepository);
        this.compressedOopsResolver = new CompressedOopsResolver(profileInfo, sessions, eventRepository, reports);
        this.uploadService = new HeapDumpUploadService(
                profileInfo, additionalFilesManager, reports, additionalFilesManager.getHeapDumpAnalysisPath());
    }

    // --- Lifecycle helpers ------------------------------------------------

    private <R> Optional<R> withSession(HeapDumpSessionTemplate.SessionWork<R> work) {
        return sessions.execute(work);
    }

    @Override
    public boolean heapDumpExists() {
        return sessions.heapDumpExists();
    }

    @Override
    public boolean isCacheReady() {
        return sessions.isCacheReady();
    }

    // --- Summary + Histogram ---------------------------------------------

    @Override
    public HeapSummary getSummary() {
        return withSession(session -> HeapSummaryAnalyzer.analyze(session.view())).orElse(null);
    }

    @Override
    public InitializeResult initialize(Boolean compressedOopsOverride) {
        // Done inside a single withSession so the index rebuild (if any) and the
        // compressed-oops inference share one HeapDumpSession. Calling
        // resolveAndStoreCompressedOops() first would otherwise open *its own*
        // session (via heap inference), trigger the index build inside that
        // throwaway session, and leave initialize()'s session with empty
        // lastBuildSubPhases().
        return withSession(session -> {
            compressedOopsResolver.resolveAndStoreInSession(compressedOopsOverride, session);
            HeapSummary summary = HeapSummaryAnalyzer.analyze(session.view());
            return new InitializeResult(summary, session.lastBuildSubPhases());
        }).orElseGet(() -> new InitializeResult(null, List.<SubPhaseTiming>of()));
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
            plan = oqlEngine.compile(stmt, new OqlCompileOptions(request.scanLargeStrings()));
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
        return withSession(session -> ThreadStackAnalyzer.getStack(session.view(), threadObjectId))
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
        uploadService.unloadHeap();
    }

    @Override
    public void deleteCache() {
        uploadService.deleteCache();
    }

    @Override
    public void deleteHeapDump() {
        uploadService.deleteHeapDump();
    }

    @Override
    public void uploadHeapDump(InputStream inputStream, String filename) {
        uploadService.upload(inputStream, filename);
    }

    @Override
    public void sanitizeHeapDump() {
        uploadService.sanitize();
    }

    // --- String analysis -------------------------------------------------

    @Override
    public boolean stringAnalysisExists() {
        return reports.exists(new StringHeapAnalysis());
    }

    @Override
    public StringAnalysisReport getStringAnalysis() {
        return reports.read(new StringHeapAnalysis()).orElse(null);
    }

    @Override
    public void runStringAnalysis(int topN) {
        runner.run(new StringHeapAnalysis(topN, jvmStringFlagsProvider.stringFlags()));
    }

    // --- Thread analysis -------------------------------------------------

    @Override
    public boolean threadAnalysisExists() {
        return reports.exists(new ThreadHeapAnalysis());
    }

    @Override
    public ThreadAnalysisReport getThreadAnalysis() {
        return reports.read(new ThreadHeapAnalysis()).orElse(null);
    }

    @Override
    public void runThreadAnalysis() {
        runner.run(new ThreadHeapAnalysis());
    }

    @Override
    public List<SubPhaseTiming> runComputeDominator() {
        return withSession(session -> session.buildDominatorTreeIfNeeded()
                .map(r -> r.subPhases())
                .orElse(List.<SubPhaseTiming>of()))
                .orElse(List.<SubPhaseTiming>of());
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
        return compressedOopsResolver.resolveAndStore(manualOverride);
    }

    @Override
    public Optional<HeapDumpConfig> getHeapDumpConfig() {
        return compressedOopsResolver.read();
    }

    // --- Init pipeline result --------------------------------------------

    @Override
    public boolean initPipelineResultExists() {
        return reports.exists(INIT_PIPELINE_RESULT_FILE);
    }

    @Override
    public Optional<InitPipelineResult> getInitPipelineResult() {
        return reports.read(INIT_PIPELINE_RESULT_FILE, InitPipelineResult.class);
    }

    @Override
    public void storeInitPipelineResult(InitPipelineResult result) {
        reports.write(INIT_PIPELINE_RESULT_FILE, result, INIT_PIPELINE_RESULT_DISPLAY_NAME);
    }

    // --- Dominator Tree --------------------------------------------------

    @Override
    public DominatorTreeResponse getDominatorTreeRoots(int limit) {
        return withSession(session -> {
            session.buildDominatorTreeIfNeeded();
            return DominatorTreeAnalyzer.children(session.view(), DOMINATOR_ROOT_ID, limit);
        }).orElse(EMPTY_DOMINATOR_TREE);
    }

    @Override
    public DominatorTreeResponse getDominatorTreeChildren(long objectId, int offset, int limit) {
        // The native DominatorTreeAnalyzer does not currently support offset-based
        // pagination; offset is ignored. The frontend uses lazy expansion, so the
        // first `limit` children is what each call needs.
        return withSession(session -> {
            session.buildDominatorTreeIfNeeded();
            return DominatorTreeAnalyzer.children(session.view(), objectId, limit);
        }).orElse(EMPTY_DOMINATOR_TREE);
    }

    // --- Collection Analysis ---------------------------------------------

    @Override
    public boolean collectionAnalysisExists() {
        return reports.exists(new CollectionHeapAnalysis());
    }

    @Override
    public CollectionAnalysisReport getCollectionAnalysis() {
        return reports.read(new CollectionHeapAnalysis()).orElse(null);
    }

    @Override
    public void runCollectionAnalysis() {
        runner.run(new CollectionHeapAnalysis());
    }

    // --- Class Instance Browser ------------------------------------------

    @Override
    public ClassInstancesResponse getClassInstances(
            String className, int limit, int offset, boolean includeRetainedSize) {
        return getClassInstances(className, limit, offset, includeRetainedSize, InstanceSortBy.OBJECT_ID);
    }

    @Override
    public ClassInstancesResponse getClassInstances(
            String className, int limit, int offset, boolean includeRetainedSize, InstanceSortBy sortBy) {
        return withSession(session -> {
            if (includeRetainedSize || sortBy == InstanceSortBy.RETAINED_SIZE) {
                session.buildDominatorTreeIfNeeded();
            }
            HeapView view = session.view();
            List<JavaClassRow> matches = view.findClassesByName(className);
            if (matches.isEmpty()) {
                return new ClassInstancesResponse(className, 0, List.of(), false);
            }
            return ClassInstanceBrowserAnalyzer.browse(view, matches.get(0).classId(), offset, limit, sortBy);
        }).orElse(new ClassInstancesResponse(className, 0, List.of(), false));
    }

    // --- Leak Suspects ---------------------------------------------------

    @Override
    public boolean leakSuspectsExists() {
        return reports.exists(new LeakSuspectsAnalysis());
    }

    @Override
    public LeakSuspectsReport getLeakSuspects() {
        return reports.read(new LeakSuspectsAnalysis()).orElse(null);
    }

    @Override
    public void runLeakSuspects() {
        runner.run(new LeakSuspectsAnalysis());
    }

    // --- Biggest Objects -------------------------------------------------

    @Override
    public boolean biggestObjectsExists() {
        return reports.exists(new BiggestObjectsAnalysis());
    }

    @Override
    public BiggestObjectsReport getBiggestObjects() {
        return reports.read(new BiggestObjectsAnalysis()).orElse(null);
    }

    @Override
    public void runBiggestObjects(int topN) {
        runner.run(new BiggestObjectsAnalysis(topN));
    }

    // --- Biggest Collections ---------------------------------------------

    @Override
    public boolean biggestCollectionsExists() {
        return reports.exists(new BiggestCollectionsAnalysis());
    }

    @Override
    public BiggestCollectionsReport getBiggestCollections() {
        return reports.read(new BiggestCollectionsAnalysis()).orElse(null);
    }

    @Override
    public void runBiggestCollections(int topN) {
        runner.run(new BiggestCollectionsAnalysis(topN));
    }

    // --- Class Loader Analysis (base + leak chains) ----------------------

    @Override
    public boolean classLoaderAnalysisExists() {
        return reports.exists(new ClassLoaderHeapAnalysis());
    }

    @Override
    public ClassLoaderReport getClassLoaderAnalysis() {
        return reports.read(new ClassLoaderHeapAnalysis()).orElse(null);
    }

    @Override
    public void runClassLoaderAnalysis() {
        runner.run(new ClassLoaderHeapAnalysis());
    }

    // --- Consumer Report -------------------------------------------------

    @Override
    public boolean consumerReportExists() {
        return reports.exists(new ConsumerReportAnalysis());
    }

    @Override
    public ConsumerReport getConsumerReport() {
        return reports.read(new ConsumerReportAnalysis()).orElse(null);
    }

    @Override
    public void runConsumerReport() {
        runner.run(new ConsumerReportAnalysis());
    }
}
