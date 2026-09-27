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


package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapLimits;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectAnswers.ClassInstances;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectAnswers.DominatorChildren;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectAnswers.DominatorEntry;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectAnswers.DominatorRoots;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectAnswers.GcRootPaths;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectAnswers.ObjectDetail;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectAnswers.PathStatus;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectAnswers.Referrers;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectAnswers.RootPath;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectAnswers;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectIds;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.GcRoots;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.HeapThread;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.Histogram;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.HistogramClass;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.RootType;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.Summary;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.Threads;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReport;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.BiggestObject;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.BiggestObjects;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.ClassLoaderChains;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.CollectionAnalysis;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.CollectionTotals;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.CollectionType;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.Component;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.Consumer;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.Contributor;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.DedupOpportunity;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.Hint;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.LeakSuspects;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.LoaderChain;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.LoaderLeak;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.StringAnalysis;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.StringTotals;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.Suspect;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers.TopConsumers;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReportAnswers;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapSqlAnswers.Column;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapSqlAnswers.DumpMetadata;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapSqlAnswers.Metadata;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapSqlAnswers.MetadataStatus;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapSqlAnswers.QueryResult;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapSqlAnswers.TableColumns;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapSqlAnswers.Tables;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HistogramOrder;
import cafe.jeffrey.microscope.core.mcp.tools.heap.ReportStatus;
import cafe.jeffrey.microscope.mcp.protocol.McpCursor;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectsReport;
import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstanceEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstancesResponse;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakChain;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.CollectionStats;
import cafe.jeffrey.profile.heapdump.model.ComponentEntry;
import cafe.jeffrey.profile.heapdump.model.ConsumerReport;
import cafe.jeffrey.profile.heapdump.model.DominatorNode;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.GCRootSummary;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.profile.heapdump.model.InstanceDetail;
import cafe.jeffrey.profile.heapdump.model.InstanceField;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeResponse;
import cafe.jeffrey.profile.heapdump.model.LeakSuspect;
import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.StringDeduplicationEntry;
import cafe.jeffrey.profile.heapdump.view.SqlQueryResult;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolRequirement;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.shared.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Heap dump tools: the {@code heap_} MCP family's readers of one indexed dump.
 * <p>
 * Every answer is a record for the structured content. The sixteen tools that describe objects and
 * reports keep the fixed-width text the model reads, rendered from that record and ended with the
 * shared footer (the page and the next calls); the four that read the index as tables answer with the
 * record's JSON. Every object id, in and out, is a decimal string.
 */
public class HeapDumpMcpTools {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpMcpTools.class);

    private static final int DEFAULT_HISTOGRAM_TOP = 50;
    private static final int MAX_HISTOGRAM_TOP = 200;
    private static final int DEFAULT_BIGGEST_TOP = 20;
    private static final int MAX_BIGGEST_TOP = 50;
    /** The page size of every paged heap listing: instances, dominator-tree nodes and referrers. */
    private static final int DEFAULT_PAGE_LIMIT = 20;
    private static final int MAX_PAGE_LIMIT = 50;

    /**
     * The dominator roots answer with the whole page by default: it is the first question asked of a
     * heap, one line per root, and the fiftieth holder is still worth seeing beside the first.
     */
    private static final int DEFAULT_DOMINATOR_ROOTS_LIMIT = 50;
    private static final int DEFAULT_GC_ROOT_PATHS = 3;
    private static final int MAX_GC_ROOT_PATHS = 5;

    private static final int LIST_TABLES_ROW_CAP = 100;
    private static final int DESCRIBE_TABLE_ROW_CAP = 200;
    private static final int EXECUTE_QUERY_ROW_CAP = 100;
    private static final int DUMP_METADATA_ROW_CAP = 1;

    private static final String BROWSE_INSTANCES_TOOL = "heap_browseClassInstances";
    private static final String INSTANCE_DETAIL_TOOL = "heap_getInstanceDetail";
    private static final String DOMINATOR_ROOTS_TOOL = "heap_getDominatorTreeRoots";
    private static final String DOMINATOR_CHILDREN_TOOL = "heap_getDominatorTreeChildren";
    private static final String PATH_TO_ROOT_TOOL = "heap_getPathToGCRoot";
    private static final String REFERRERS_TOOL = "heap_getReferrers";
    private static final String HISTOGRAM_TOOL = "heap_getClassHistogram";
    private static final String LEAK_SUSPECTS_TOOL = "heap_getLeakSuspects";
    private static final String SUMMARY_TOOL = "heap_getHeapSummary";
    private static final String DESCRIBE_TABLE_TOOL = "heap_describeTable";
    private static final String EXECUTE_QUERY_TOOL = "heap_executeQuery";
    private static final String PREPARE_TOOL = "heap_prepare";
    private static final String STATUS_TOOL = "heap_status";

    private static final String PROFILE_ID = "profileId";
    private static final String CLASS_NAME = "className";
    private static final String LIMIT = "limit";
    private static final String CURSOR = "cursor";
    private static final String REPORT = "report";
    private static final String TABLE_NAME = "tableName";
    private static final String QUERY = "query";
    private static final String STRING_CLASS = "java.lang.String";
    private static final String SAMPLE_QUERY = "SELECT * FROM %s LIMIT 10";
    private static final String NULLABLE_YES = "YES";

    private static final String LIST_TABLES_SQL = "SELECT table_name FROM information_schema.tables "
            + "WHERE table_schema = 'main' AND table_name NOT LIKE 'flyway_%' ORDER BY table_name";
    private static final String DESCRIBE_TABLE_SQL = "SELECT column_name, data_type, is_nullable "
            + "FROM information_schema.columns WHERE table_schema = 'main' AND table_name = '%s' "
            + "ORDER BY ordinal_position";

    private static final String ID_SIZE = "id_size";
    private static final String HPROF_VERSION = "hprof_version";
    private static final String COMPRESSED_OOPS = "compressed_oops";
    private static final String BYTES_PARSED = "bytes_parsed";
    private static final String RECORD_COUNT = "record_count";
    private static final String WARNING_COUNT = "warning_count";
    private static final String TRUNCATED = "truncated";
    private static final String PARSER_VERSION = "parser_version";
    private static final String PARSED_AT_MS = "parsed_at_ms";
    private static final String DUMP_METADATA_SQL = "SELECT " + String.join(", ", ID_SIZE, HPROF_VERSION,
            COMPRESSED_OOPS, BYTES_PARSED, RECORD_COUNT, WARNING_COUNT, TRUNCATED, PARSER_VERSION, PARSED_AT_MS)
            + " FROM dump_metadata";

    private static final String NOT_RUN_YET =
            "%s has not been run for this heap dump yet. heap_prepare with report %s computes it, and "
                    + "heap_status reports its progress; the matching page in the Jeffrey UI can run it too.";
    private static final String UNKNOWN_OBJECT =
            "No object with id %s is in this heap dump. Object ids come from heap_browseClassInstances, "
                    + "heap_getDominatorTreeRoots and the report tools.";
    private static final String NO_PATH =
            "No path from a GC root reaches object %s within " + HeapLimits.PATH_SEARCH_HOPS
                    + " hops; weak references are not followed.";
    private static final String IS_GC_ROOT =
            "Object %s is itself a GC root (%s): it is kept alive directly, and no longer path was found.";
    /** How the string report ends a preview it cut from a longer string. */
    private static final String PREVIEW_ELLIPSIS = "\u2026";
    private static final String NO_METADATA = "The heap-dump index holds no metadata row.";

    private static final String NOTE_INSTANCES =
            "The histogram page lists the class; its instances open from the class's row there.";
    private static final String NOTE_OBJECT =
            "The page runs the path-to-GC-root search for this object; the rest of this answer is not on it.";
    private static final String NOTE_CHILDREN =
            "The page opens at the dominator-tree roots; expand down to object %s there.";

    private static final String WHY_HISTOGRAM = "ranks the classes by the bytes their instances take";
    private static final String WHY_DOMINATOR_ROOTS = "names the objects that keep the most memory alive";
    private static final String WHY_LEAK_SUSPECTS = "reads the leak-suspect report";
    private static final String WHY_BROWSE = "lists that class's instances with the object ids the object tools take";
    private static final String WHY_BROWSE_STRINGS = "lists String instances with their object ids";
    private static final String WHY_NEXT_PAGE = "reads the next page";
    private static final String WHY_INSTANCE = "opens that object with its fields";
    private static final String WHY_PATH = "says why that object is still reachable";
    private static final String WHY_CHILDREN = "lists what that object retains";
    private static final String WHY_REFERRERS = "lists the objects that reference it";
    private static final String WHY_PREPARE = "computes this report; the answer names the operation to follow";
    private static final String WHY_STATUS = "reports how far a preparation has got";
    private static final String WHY_DESCRIBE = "gives that table's columns";
    private static final String WHY_SAMPLE = "samples that table's rows";
    private static final String WHY_SUMMARY = "summarises the whole heap";
    private static final String WHY_REFERENCED = "opens the object its first reference field points to";
    private static final String WHY_ROOT = "opens the GC root that holds the path";

    private static final String OBSERVATION =
            "A size is an observation, not a cause: heap_getPathToGCRoot on an object id says why it is still "
                    + "reachable, which is what makes a leak claim checkable.";
    private static final String RETAINED_NEED_DOMINATOR =
            "Retained sizes come from the dominator tree; before heap_prepare builds it they are missing, not zero.";
    private static final String CAPPED_GUIDANCE =
            "Rows were left out, by the row cap or to fit one answer: tighten the WHERE clause, select fewer "
                    + "columns or aggregate instead of paging.";
    private static final String ROW_TOO_WIDE =
            "One result row is larger than an answer can carry; select fewer or narrower columns.";

    private final HeapDumpToolsDelegate delegate;
    private final String profileId;
    private final AdvertisedFamilies advertised;

    /**
     * @param profileId  the profile whose dump the delegate reads, which every next call names
     * @param advertised the families this installation serves, which gate the next calls
     */
    public HeapDumpMcpTools(HeapDumpToolsDelegate delegate, String profileId, AdvertisedFamilies advertised) {
        this.delegate = delegate;
        this.profileId = profileId;
        this.advertised = advertised;
    }

    @Tool(description = "Summarises the heap dump: total live bytes, total live instances, number of "
            + "classes and number of GC roots, and when the dump was taken as UTC epoch milliseconds - the "
            + "overall heap state.")
    @McpOutputSchema(Summary.class)
    @McpToolMeta(cost = McpToolCost.CHEAP, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult getHeapSummary() {
        HeapSummary summary = read("heap summary", delegate::getSummary);
        Summary answer = new Summary(profileId, summary.totalBytes(), summary.totalInstances(), summary.classCount(),
                summary.gcRootCount(), summary.timestamp() == null ? null : summary.timestamp().toEpochMilli(),
                steps()
                        .next(onProfile(HISTOGRAM_TOOL).why(WHY_HISTOGRAM))
                        .next(onProfile(DOMINATOR_ROOTS_TOOL).why(WHY_DOMINATOR_ROOTS))
                        .next(onProfile(LEAK_SUSPECTS_TOOL).why(WHY_LEAK_SUSPECTS))
                        .followUp(),
                link(MicroscopeView.HEAP_DUMP_OVERVIEW));
        return footed(HeapOverviewAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), null);
    }

    @Tool(description = "Ranks classes by memory usage or instance count, with the class name, instance "
            + "count and total shallow bytes of each. omittedClasses says what the cap left out: 0 below it, "
            + "null when the ranking reached it.")
    @McpOutputSchema(Histogram.class)
    @McpToolMeta(cost = McpToolCost.MODERATE, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult getClassHistogram(
            @ToolParam(required = false, description = "Number of top classes to return (default: "
                    + DEFAULT_HISTOGRAM_TOP + ", max: " + MAX_HISTOGRAM_TOP + ")")
            @ToolParamBounds(defaultValue = DEFAULT_HISTOGRAM_TOP, min = 1, max = MAX_HISTOGRAM_TOP)
            Integer topN,
            @ToolParam(required = false, description = "What to rank by: SIZE (default) or COUNT")
            HistogramOrder sortBy) {
        int top = ToolArguments.boundedLimit(topN, DEFAULT_HISTOGRAM_TOP, MAX_HISTOGRAM_TOP);
        HistogramOrder order = sortBy == null ? HistogramOrder.SIZE : sortBy;
        List<ClassHistogramEntry> entries = read("class histogram", () -> delegate.getClassHistogram(top, order.sortBy()));
        List<HistogramClass> classes = entries.stream()
                .map(entry -> new HistogramClass(entry.className(), entry.instanceCount(), entry.totalSize()))
                .toList();
        Optional<HistogramClass> first = classes.stream().findFirst();
        Histogram answer = new Histogram(profileId, order, top, classes, omittedBelowCap(classes.size(), top),
                steps()
                        .nextWhen(first.isPresent(), onProfile(BROWSE_INSTANCES_TOOL)
                                .with(CLASS_NAME, first.map(HistogramClass::className).orElse(null)).why(WHY_BROWSE))
                        .next(onProfile(DOMINATOR_ROOTS_TOOL).why(WHY_DOMINATOR_ROOTS))
                        .guidance(OBSERVATION)
                        .followUp(),
                link(MicroscopeView.HEAP_DUMP_HISTOGRAM));
        return footed(HeapOverviewAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), null);
    }

    @Tool(description = "Ranks the biggest individual objects in the heap by retained size - the single "
            + "objects that hold the most memory - with their object ids. Reads the biggest-objects report "
            + "heap_prepare builds; until it is built the status is NOT_RUN_YET.")
    @McpOutputSchema(BiggestObjects.class)
    @McpToolMeta(cost = McpToolCost.CHEAP,
            requires = {McpToolRequirement.HEAP_DUMP_INDEXED, McpToolRequirement.HEAP_REPORTS})
    public McpToolResult getBiggestObjects(
            @ToolParam(required = false, description = "Number of biggest objects to return (default: "
                    + DEFAULT_BIGGEST_TOP + ", max: " + MAX_BIGGEST_TOP + ")")
            @ToolParamBounds(defaultValue = DEFAULT_BIGGEST_TOP, min = 1, max = MAX_BIGGEST_TOP)
            Integer topN) {
        int top = ToolArguments.boundedLimit(topN, DEFAULT_BIGGEST_TOP, MAX_BIGGEST_TOP);
        String link = link(MicroscopeView.HEAP_DUMP_BIGGEST_OBJECTS);
        BiggestObjectsReport report = read("biggest objects", () -> delegate.getBiggestObjects(top));
        BiggestObjects answer;
        if (report == null) {
            answer = new BiggestObjects(ReportStatus.NOT_RUN_YET, notRunYet("Biggest objects analysis", HeapReport.BIGGEST),
                    profileId, null, null, List.of(), null, notRunYetSteps(HeapReport.BIGGEST), link);
        } else {
            List<BiggestObject> objects = report.entries().stream()
                    .map(entry -> new BiggestObject(HeapObjectIds.format(entry.objectId()), entry.className(),
                            entry.shallowSize(), entry.retainedSize()))
                    .toList();
            Optional<String> first = objects.stream().findFirst().map(BiggestObject::objectId);
            answer = new BiggestObjects(ReportStatus.OK, null, profileId, report.totalHeapSize(),
                    report.totalRetainedSize(), objects, omittedBelowCap(objects.size(), top),
                    objectSteps(first).guidance(OBSERVATION).followUp(), link);
        }
        return footed(HeapReportAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), null);
    }

    @Tool(description = "Returns leak suspects found by heuristics: single objects with a disproportionate "
            + "retained size, or classes whose many instances together hold significant memory, with the "
            + "object ids of each cluster root and the class loaders involved. Reads the leak-suspect report "
            + "heap_prepare builds; until it is built the status is NOT_RUN_YET.")
    @McpOutputSchema(LeakSuspects.class)
    @McpToolMeta(cost = McpToolCost.CHEAP,
            requires = {McpToolRequirement.HEAP_DUMP_INDEXED, McpToolRequirement.HEAP_REPORTS})
    public McpToolResult getLeakSuspects() {
        String link = link(MicroscopeView.HEAP_DUMP_LEAK_SUSPECTS);
        LeakSuspectsReport report = read("leak suspects", delegate::getLeakSuspects);
        LeakSuspects answer;
        if (report == null) {
            answer = new LeakSuspects(ReportStatus.NOT_RUN_YET, notRunYet("Leak suspects analysis", HeapReport.LEAKS),
                    profileId, null, null, List.of(), List.of(), notRunYetSteps(HeapReport.LEAKS), link);
        } else {
            List<Suspect> suspects = report.suspects().stream().map(HeapDumpMcpTools::suspect).toList();
            List<LoaderLeak> loaders = listOf(report.topLeakingClassLoaders()).stream()
                    .map(loader -> new LoaderLeak(HeapObjectIds.format(loader.classLoaderId()),
                            loader.classLoaderClassName(), loader.totalRetainedSize(), loader.suspectCount()))
                    .toList();
            Optional<String> first = suspects.stream()
                    .map(suspect -> suspect.objectId() != null ? suspect.objectId() : suspect.accumulationPointObjectId())
                    .filter(Objects::nonNull)
                    .findFirst();
            answer = new LeakSuspects(ReportStatus.OK, null, profileId, report.totalHeapSize(), report.analyzedBytes(),
                    suspects, loaders, objectSteps(first).guidance(OBSERVATION).followUp(), link);
        }
        return footed(HeapReportAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), null);
    }

    @Tool(description = "Returns class-loader leak chains: for each suspicious class loader (large "
            + "retained size, duplicate classes, or a webapp/URL loader), the GC-root path keeping it alive "
            + "and any matched leak-pattern hints (ThreadLocal, JDBC driver, JNI global, ServiceLoader, "
            + "static Logger, contextClassLoader). The canonical Tomcat-redeploy diagnostic. Reads the "
            + "class-loader report heap_prepare builds; until it is built the status is NOT_RUN_YET.")
    @McpOutputSchema(ClassLoaderChains.class)
    @McpToolMeta(cost = McpToolCost.CHEAP,
            requires = {McpToolRequirement.HEAP_DUMP_INDEXED, McpToolRequirement.HEAP_REPORTS})
    public McpToolResult getClassLoaderLeakChains() {
        String link = link(MicroscopeView.HEAP_DUMP_CLASSLOADER_ANALYSIS);
        ClassLoaderReport report = read("class-loader leak chains", delegate::getClassLoaderAnalysis);
        ClassLoaderChains answer;
        if (report == null) {
            answer = new ClassLoaderChains(ReportStatus.NOT_RUN_YET,
                    notRunYet("Class loader analysis", HeapReport.CLASSLOADERS), profileId, List.of(), null,
                    notRunYetSteps(HeapReport.CLASSLOADERS), link);
        } else {
            List<ClassLoaderLeakChain> all = listOf(report.leakChains()).stream()
                    .sorted(Comparator.comparingLong(ClassLoaderLeakChain::retainedSize).reversed())
                    .toList();
            List<LoaderChain> chains = all.stream().limit(HeapLimits.LOADER_CHAINS).map(HeapDumpMcpTools::chain).toList();
            Optional<String> first = chains.stream().findFirst().map(LoaderChain::classLoaderId);
            answer = new ClassLoaderChains(ReportStatus.OK, null, profileId, chains,
                    omittedBelowEngineCap(all.size(), chains.size(), HeapLimits.ENGINE_LOADER_CHAINS),
                    steps()
                            .nextWhen(first.isPresent(), onObject(INSTANCE_DETAIL_TOOL, first).why(WHY_INSTANCE))
                            .nextWhen(first.isPresent(), onObject(REFERRERS_TOOL, first).why(WHY_REFERRERS))
                            .followUp(),
                    link);
        }
        return footed(HeapReportAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), null);
    }

    @Tool(description = "Ranks the top memory consumers grouped by (package, class loader) by shallow size - "
            + "the twenty largest. Answers 'which subsystem is the most bloated?'. The report computes no "
            + "retained size and no per-package component report, so components is null. Reads the consumer "
            + "report heap_prepare builds; until it is built the status is NOT_RUN_YET.")
    @McpOutputSchema(TopConsumers.class)
    @McpToolMeta(cost = McpToolCost.CHEAP,
            requires = {McpToolRequirement.HEAP_DUMP_INDEXED, McpToolRequirement.HEAP_REPORTS})
    public McpToolResult getTopConsumers() {
        String link = link(MicroscopeView.HEAP_DUMP_CONSUMERS);
        ConsumerReport report = read("top consumers", delegate::getConsumerReport);
        TopConsumers answer;
        if (report == null) {
            answer = new TopConsumers(ReportStatus.NOT_RUN_YET, notRunYet("Consumer report", HeapReport.CONSUMERS),
                    profileId, null, List.of(), null, null, null, notRunYetSteps(HeapReport.CONSUMERS), link);
        } else {
            List<Consumer> consumers = listOf(report.topConsumers()).stream()
                    .limit(HeapLimits.CONSUMERS)
                    .map(entry -> new Consumer(entry.packageName(), HeapObjectIds.format(entry.classLoaderId()),
                            entry.classLoaderClassName(), entry.shallowSize(), entry.classCount(),
                            entry.instanceCount()))
                    .toList();
            // The engine does not build the per-package component report yet: an empty one is "not
            // computed", not "no packages".
            List<ComponentEntry> allComponents = listOf(report.componentReport());
            List<Component> components = allComponents.isEmpty() ? null : allComponents.stream()
                    .limit(HeapLimits.CONSUMERS)
                    .map(entry -> new Component(entry.packageName(), entry.shallowSize(), entry.classCount(),
                            entry.instanceCount()))
                    .toList();
            answer = new TopConsumers(ReportStatus.OK, null, profileId, report.totalHeapSize(),
                    consumers, omittedBelowEngineCap(listOf(report.topConsumers()).size(), consumers.size(),
                            HeapLimits.ENGINE_CONSUMERS),
                    components, components == null ? null : allComponents.size() - components.size(),
                    steps()
                            .next(onProfile(HISTOGRAM_TOOL).why(WHY_HISTOGRAM))
                            .next(onProfile(DOMINATOR_ROOTS_TOOL).why(WHY_DOMINATOR_ROOTS))
                            .followUp(),
                    link);
        }
        return footed(HeapReportAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), null);
    }

    @Tool(description = "Returns duplicate strings and the memory wasted by string duplication: totals and "
            + "the twenty deduplication opportunities with the biggest savings, each with a preview of up to "
            + HeapLimits.STRING_CONTENT_CHARS + " characters and whether it was cut. Reads the string report "
            + "heap_prepare builds; until it is built the status is NOT_RUN_YET.")
    @McpOutputSchema(StringAnalysis.class)
    @McpToolMeta(cost = McpToolCost.CHEAP,
            requires = {McpToolRequirement.HEAP_DUMP_INDEXED, McpToolRequirement.HEAP_REPORTS})
    public McpToolResult getStringAnalysis() {
        String link = link(MicroscopeView.HEAP_DUMP_STRING_ANALYSIS);
        StringAnalysisReport report = read("string analysis", delegate::getStringAnalysis);
        StringAnalysis answer;
        if (report == null) {
            answer = new StringAnalysis(ReportStatus.NOT_RUN_YET, notRunYet("String analysis", HeapReport.STRINGS),
                    profileId, null, List.of(), null, notRunYetSteps(HeapReport.STRINGS), link);
        } else {
            List<StringDeduplicationEntry> all = listOf(report.opportunities());
            List<DedupOpportunity> opportunities = all.stream()
                    .limit(HeapLimits.STRING_OPPORTUNITIES)
                    .map(entry -> new DedupOpportunity(entry.content(),
                            entry.content() != null && entry.content().endsWith(PREVIEW_ELLIPSIS), entry.count(),
                            entry.arraySize(), entry.savings()))
                    .toList();
            answer = new StringAnalysis(ReportStatus.OK, null, profileId,
                    new StringTotals(report.totalStrings(), report.totalStringShallowSize(), report.uniqueArrays(),
                            report.sharedArrays(), report.memorySavedByDedup(), report.potentialSavings()),
                    opportunities,
                    report.topN() == null
                            ? null
                            : omittedBelowEngineCap(all.size(), opportunities.size(), report.topN()),
                    steps()
                            .next(onProfile(BROWSE_INSTANCES_TOOL).with(CLASS_NAME, STRING_CLASS).why(WHY_BROWSE_STRINGS))
                            .followUp(),
                    link);
        }
        return footed(HeapReportAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), null);
    }

    @Tool(description = "Returns the empty, singleton and oversized collections (HashMap, ArrayList, "
            + "HashSet, etc.) with their fill ratios and wasted bytes per collection type. Reads the "
            + "collection report heap_prepare builds; until it is built the status is NOT_RUN_YET.")
    @McpOutputSchema(CollectionAnalysis.class)
    @McpToolMeta(cost = McpToolCost.CHEAP,
            requires = {McpToolRequirement.HEAP_DUMP_INDEXED, McpToolRequirement.HEAP_REPORTS})
    public McpToolResult getCollectionAnalysis() {
        String link = link(MicroscopeView.HEAP_DUMP_COLLECTION_ANALYSIS);
        CollectionAnalysisReport report = read("collection analysis", delegate::getCollectionAnalysis);
        CollectionAnalysis answer;
        if (report == null) {
            answer = new CollectionAnalysis(ReportStatus.NOT_RUN_YET,
                    notRunYet("Collection analysis", HeapReport.COLLECTIONS), profileId, null, List.of(),
                    notRunYetSteps(HeapReport.COLLECTIONS), link);
        } else {
            List<CollectionType> types = listOf(report.byType()).stream()
                    .map(type -> new CollectionType(type.collectionType(), type.totalCount(), type.emptyCount(),
                            type.totalWastedBytes(), type.avgFillRatio()))
                    .toList();
            Optional<String> worst = listOf(report.byType()).stream()
                    .max(Comparator.comparingLong(CollectionStats::totalWastedBytes))
                    .map(CollectionStats::collectionType);
            answer = new CollectionAnalysis(ReportStatus.OK, null, profileId,
                    new CollectionTotals(report.totalCollections(), report.totalEmptyCount(), report.totalWastedBytes()),
                    types,
                    steps()
                            .nextWhen(worst.isPresent(), onProfile(BROWSE_INSTANCES_TOOL)
                                    .with(CLASS_NAME, worst.orElse(null)).why(WHY_BROWSE))
                            .followUp(),
                    link);
        }
        return footed(HeapReportAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), null);
    }

    @Tool(description = "Returns the threads in the heap dump with their object ids, names, states and "
            + "what each retains - the " + HeapLimits.THREADS + " retaining the most, with omittedThreads "
            + "counting the rest.")
    @McpOutputSchema(Threads.class)
    @McpToolMeta(cost = McpToolCost.MODERATE, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult getThreads() {
        List<HeapThreadInfo> all = read("threads", delegate::getThreads);
        List<HeapThread> threads = all.stream()
                .sorted(Comparator.comparing(HeapThreadInfo::retainedSize,
                        Comparator.nullsLast(Comparator.<Long>reverseOrder())))
                .limit(HeapLimits.THREADS)
                .map(thread -> new HeapThread(HeapObjectIds.format(thread.objectId()), thread.name(), thread.daemon(),
                        thread.priority(), thread.retainedSize(), thread.frameCount(), thread.localsCount(),
                        thread.localsBytes(), thread.state()))
                .toList();
        Optional<String> first = threads.stream().findFirst().map(HeapThread::objectId);
        Threads answer = new Threads(profileId, all.size(), threads, all.size() - threads.size(),
                steps()
                        .nextWhen(first.isPresent(), onObject(INSTANCE_DETAIL_TOOL, first).why(WHY_INSTANCE))
                        .nextWhen(first.isPresent(), onObject(DOMINATOR_CHILDREN_TOOL, first).why(WHY_CHILDREN))
                        .followUp(),
                link(MicroscopeView.HEAP_DUMP_THREADS));
        return footed(HeapOverviewAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), null);
    }

    @Tool(description = "Summarises the GC roots in the heap by type and count, the most common type first.")
    @McpOutputSchema(GcRoots.class)
    @McpToolMeta(cost = McpToolCost.CHEAP, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult getGCRootSummary() {
        GCRootSummary summary = read("GC root summary", delegate::getGCRootSummary);
        List<RootType> types = summary.rootsByType().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new RootType(entry.getKey(), entry.getValue()))
                .toList();
        GcRoots answer = new GcRoots(profileId, summary.totalRoots(), types,
                steps().next(onProfile(DOMINATOR_ROOTS_TOOL).why(WHY_DOMINATOR_ROOTS)).followUp(),
                link(MicroscopeView.HEAP_DUMP_GC_ROOTS));
        return footed(HeapOverviewAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), null);
    }

    @Tool(description = "Pages through the instances of one class - a class from the histogram, say - with "
            + "their object ids and shallow sizes. A page that continues hands back nextCursor.")
    @McpOutputSchema(ClassInstances.class)
    @McpToolMeta(cost = McpToolCost.MODERATE, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult browseClassInstances(
            @ToolParam(required = true, description = "Fully qualified class name (e.g., 'java.lang.String', 'java.util.HashMap')")
            String className,
            @ToolParam(required = false, description = "Maximum number of instances to return (default: "
                    + DEFAULT_PAGE_LIMIT + ", max: " + MAX_PAGE_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_PAGE_LIMIT, min = 1, max = MAX_PAGE_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "nextCursor from the previous page, passed unchanged with "
                    + "the same className; omit for the first page")
            String cursor) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className is required: the fully qualified class to list");
        }
        String name = className.trim();
        int pageSize = ToolArguments.boundedLimit(limit, DEFAULT_PAGE_LIMIT, MAX_PAGE_LIMIT);
        McpCursor.Filters filters = McpCursor.Filters.of(BROWSE_INSTANCES_TOOL, profileId, name);
        int offset = OffsetPaging.offset(cursor, filters);

        ClassInstancesResponse response = read("class instances",
                () -> delegate.getClassInstances(name, pageSize, offset, false));
        List<HeapObjectAnswers.Instance> instances = response.instances().stream()
                .map(HeapDumpMcpTools::instance)
                .toList();
        McpCursor.Next next = OffsetPaging.next(filters, offset, instances.size(), response.hasMore());
        Optional<String> first = instances.stream().findFirst().map(HeapObjectAnswers.Instance::objectId);
        ClassInstances answer = new ClassInstances(profileId, name, response.totalInstances(), instances,
                next.hasMore(), next.nextCursor(),
                steps()
                        .nextWhen(next.hasMore(), onProfile(BROWSE_INSTANCES_TOOL).with(CLASS_NAME, name)
                                .with(LIMIT, pageSize).with(CURSOR, next.nextCursor()).why(WHY_NEXT_PAGE))
                        .nextWhen(first.isPresent(), onObject(INSTANCE_DETAIL_TOOL, first).why(WHY_INSTANCE))
                        .nextWhen(first.isPresent(), onObject(PATH_TO_ROOT_TOOL, first).why(WHY_PATH))
                        .followUp(),
                link(MicroscopeView.HEAP_DUMP_HISTOGRAM), NOTE_INSTANCES);
        return footed(HeapObjectAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), answer.uiLinkNote());
    }

    @Tool(description = "Returns one object instance in detail, with its fields and their values; a "
            + "reference field names the object it points to by id. An id no object has is refused.")
    @McpOutputSchema(HeapObjectAnswers.InstanceDetail.class)
    @McpToolMeta(cost = McpToolCost.CHEAP, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult getInstanceDetail(
            @ToolParam(required = true, description = "The object id of the instance to inspect, a decimal string "
                    + "as the heap tools return it")
            String objectId) {
        long id = existing(objectId);
        String canonical = HeapObjectIds.format(id);
        String link = objectLink(canonical);
        InstanceDetail detail = read("instance detail", () -> delegate.getInstanceDetail(id, false));
        if (detail == null) {
            throw new ToolExecutionException(UNKNOWN_OBJECT.formatted(canonical));
        }
        List<InstanceField> all = listOf(detail.fields());
        List<HeapObjectAnswers.Field> fields = all.stream()
                .limit(HeapLimits.INSTANCE_FIELDS)
                .map(field -> new HeapObjectAnswers.Field(field.name(), field.type(),
                        cut(field.value(), HeapLimits.VALUE_CHARS),
                        HeapObjectIds.formatNullable(field.referencedObjectId()), field.referencedClassName()))
                .toList();
        Optional<String> referenced = fields.stream()
                .map(HeapObjectAnswers.Field::referencedObjectId)
                .filter(Objects::nonNull)
                .findFirst();
        Optional<String> self = Optional.of(canonical);
        HeapObjectAnswers.InstanceDetail answer = new HeapObjectAnswers.InstanceDetail(profileId, canonical,
                new ObjectDetail(detail.className(), detail.shallowSize(), detail.retainedSize(),
                        cut(detail.displayValue(), HeapLimits.VALUE_CHARS), fields, all.size() - fields.size()),
                steps()
                        .next(onObject(PATH_TO_ROOT_TOOL, self).why(WHY_PATH))
                        .next(onObject(REFERRERS_TOOL, self).why(WHY_REFERRERS))
                        .nextWhen(referenced.isPresent(), onObject(INSTANCE_DETAIL_TOOL, referenced)
                                .why(WHY_REFERENCED))
                        .followUp(),
                link, NOTE_OBJECT);
        return footed(HeapObjectAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), answer.uiLinkNote());
    }

    @Tool(description = "Returns the dominator tree roots - the objects with the largest retained size in "
            + "the heap, the ones responsible for keeping the others alive - with their object ids. "
            + "truncated says whether more roots exist beyond the limit.")
    @McpOutputSchema(DominatorRoots.class)
    @McpToolMeta(maxResultSizeChars = McpToolOutput.MAX_CHARS, cost = McpToolCost.EXPENSIVE,
            requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult getDominatorTreeRoots(
            @ToolParam(required = false, description = "Maximum number of root entries to return (default: "
                    + DEFAULT_DOMINATOR_ROOTS_LIMIT + ", max: " + MAX_PAGE_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_DOMINATOR_ROOTS_LIMIT, min = 1, max = MAX_PAGE_LIMIT)
            Integer limit) {
        int top = ToolArguments.boundedLimit(limit, DEFAULT_DOMINATOR_ROOTS_LIMIT, MAX_PAGE_LIMIT);
        DominatorTreeResponse response = read("dominator tree roots", () -> delegate.getDominatorTreeRoots(top));
        List<DominatorEntry> roots = response.nodes().stream().map(HeapDumpMcpTools::dominatorEntry).toList();
        Optional<String> first = roots.stream().findFirst().map(DominatorEntry::objectId);
        Optional<String> expandable = firstWithChildren(roots);
        DominatorRoots answer = new DominatorRoots(profileId, response.totalHeapSize(), roots, top, response.hasMore(),
                steps()
                        .nextWhen(expandable.isPresent(), onObject(DOMINATOR_CHILDREN_TOOL, expandable).why(WHY_CHILDREN))
                        .nextWhen(first.isPresent(), onObject(PATH_TO_ROOT_TOOL, first).why(WHY_PATH))
                        .followUp(),
                link(MicroscopeView.HEAP_DUMP_DOMINATOR_TREE));
        return footed(HeapObjectAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), null);
    }

    @Tool(description = "Returns the children of a dominator-tree node - the objects the given object "
            + "retains - one level below an entry from heap_getDominatorTreeRoots, largest first. A page "
            + "that continues hands back nextCursor.")
    @McpOutputSchema(DominatorChildren.class)
    @McpToolMeta(cost = McpToolCost.EXPENSIVE, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult getDominatorTreeChildren(
            @ToolParam(required = true, description = "Object id of the parent node in the dominator tree, a "
                    + "decimal string")
            String objectId,
            @ToolParam(required = false, description = "Maximum number of children to return (default: "
                    + DEFAULT_PAGE_LIMIT + ", max: " + MAX_PAGE_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_PAGE_LIMIT, min = 1, max = MAX_PAGE_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "nextCursor from the previous page, passed unchanged with "
                    + "the same objectId; omit for the first page")
            String cursor) {
        long id = existing(objectId);
        String canonical = HeapObjectIds.format(id);
        int pageSize = ToolArguments.boundedLimit(limit, DEFAULT_PAGE_LIMIT, MAX_PAGE_LIMIT);
        McpCursor.Filters filters = McpCursor.Filters.of(DOMINATOR_CHILDREN_TOOL, profileId, canonical);
        int offset = OffsetPaging.offset(cursor, filters);

        DominatorTreeResponse response = read("dominator tree children",
                () -> delegate.getDominatorTreeChildren(id, pageSize, offset));
        List<DominatorEntry> children = response.nodes().stream().map(HeapDumpMcpTools::dominatorEntry).toList();
        // The engine does not count a node's children, so the page continues on its own say-so.
        McpCursor.Next next = OffsetPaging.next(filters, offset, children.size(), response.hasMore());
        Optional<String> expandable = firstWithChildren(children);
        DominatorChildren answer = new DominatorChildren(profileId, canonical, children, next.hasMore(),
                next.nextCursor(),
                steps()
                        .nextWhen(expandable.isPresent(), onObject(DOMINATOR_CHILDREN_TOOL, expandable).why(WHY_CHILDREN))
                        .nextWhen(next.hasMore(), onObject(DOMINATOR_CHILDREN_TOOL, Optional.of(canonical))
                                .with(LIMIT, pageSize).with(CURSOR, next.nextCursor()).why(WHY_NEXT_PAGE))
                        .next(onObject(PATH_TO_ROOT_TOOL, Optional.of(canonical)).why(WHY_PATH))
                        .followUp(),
                link(MicroscopeView.HEAP_DUMP_DOMINATOR_TREE), NOTE_CHILDREN.formatted(canonical));
        return footed(HeapObjectAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), answer.uiLinkNote());
    }

    @Tool(description = "Finds the shortest reference chain(s) from GC roots to a given object - why it is "
            + "kept alive and cannot be garbage collected. Essential for memory leak analysis. A path longer "
            + "than " + 2 * HeapLimits.PATH_END_STEPS + " hops keeps both ends and counts the hops between. An "
            + "object that is itself a GC root answers IS_GC_ROOT; no path within " + HeapLimits.PATH_SEARCH_HOPS
            + " hops, weak references not followed, answers NO_PATH.")
    @McpOutputSchema(GcRootPaths.class)
    @McpToolMeta(cost = McpToolCost.EXPENSIVE, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult getPathToGCRoot(
            @ToolParam(required = true, description = "Object id of the target object, a decimal string")
            String objectId,
            @ToolParam(required = false, description = "Maximum number of paths to return (default: "
                    + DEFAULT_GC_ROOT_PATHS + ", max: " + MAX_GC_ROOT_PATHS + ")")
            @ToolParamBounds(defaultValue = DEFAULT_GC_ROOT_PATHS, min = 1, max = MAX_GC_ROOT_PATHS)
            Integer maxPaths) {
        long id = existing(objectId);
        String canonical = HeapObjectIds.format(id);
        int paths = ToolArguments.boundedLimit(maxPaths, DEFAULT_GC_ROOT_PATHS, MAX_GC_ROOT_PATHS);
        List<GCRootPath> found = read("path to GC root", () -> delegate.getPathsToGCRoot(id, true, paths));
        List<RootPath> rootPaths = found.stream().map(RootPath::of).toList();
        String rootKind = read("GC root kind", () -> delegate.gcRootKind(id)).orElse(null);
        Optional<String> self = Optional.of(canonical);
        McpFollowUp referrers = steps().next(onObject(REFERRERS_TOOL, self).why(WHY_REFERRERS)).followUp();
        GcRootPaths answer;
        if (rootPaths.isEmpty() && rootKind != null) {
            answer = new GcRootPaths(PathStatus.IS_GC_ROOT, IS_GC_ROOT.formatted(canonical, rootKind), profileId,
                    canonical, rootKind, List.of(), referrers, objectLink(canonical));
        } else if (rootPaths.isEmpty()) {
            answer = new GcRootPaths(PathStatus.NO_PATH, NO_PATH.formatted(canonical), profileId, canonical, null,
                    List.of(), referrers, objectLink(canonical));
        } else {
            Optional<String> root = Optional.of(rootPaths.getFirst().rootObjectId());
            answer = new GcRootPaths(PathStatus.OK, null, profileId, canonical, rootKind, rootPaths,
                    steps()
                            .next(onObject(REFERRERS_TOOL, self).why(WHY_REFERRERS))
                            .next(onObject(INSTANCE_DETAIL_TOOL, root).why(WHY_ROOT))
                            .followUp(),
                    objectLink(canonical));
        }
        return footed(HeapObjectAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), null);
    }

    @Tool(description = "Returns the objects that reference a given object (its referrers, or incoming "
            + "references) - what keeps it alive - with their object ids and the field that holds it. A page "
            + "that continues hands back nextCursor.")
    @McpOutputSchema(Referrers.class)
    @McpToolMeta(cost = McpToolCost.MODERATE, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult getReferrers(
            @ToolParam(required = true, description = "Object id to find referrers for, a decimal string")
            String objectId,
            @ToolParam(required = false, description = "Maximum number of referrers to return (default: "
                    + DEFAULT_PAGE_LIMIT + ", max: " + MAX_PAGE_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_PAGE_LIMIT, min = 1, max = MAX_PAGE_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "nextCursor from the previous page, passed unchanged with "
                    + "the same objectId; omit for the first page")
            String cursor) {
        long id = existing(objectId);
        String canonical = HeapObjectIds.format(id);
        int pageSize = ToolArguments.boundedLimit(limit, DEFAULT_PAGE_LIMIT, MAX_PAGE_LIMIT);
        McpCursor.Filters filters = McpCursor.Filters.of(REFERRERS_TOOL, profileId, canonical);
        int offset = OffsetPaging.offset(cursor, filters);

        InstanceTreeResponse response = read("referrers", () -> delegate.getReferrers(id, pageSize, offset));
        List<HeapObjectAnswers.Referrer> referrers = response.children().stream()
                .map(node -> new HeapObjectAnswers.Referrer(HeapObjectIds.format(node.objectId()), node.className(),
                        node.shallowSize(), node.fieldName()))
                .toList();
        McpCursor.Next next = OffsetPaging.next(filters, offset, referrers.size(), response.hasMore());
        Optional<String> self = Optional.of(canonical);
        Referrers answer = new Referrers(profileId, canonical, response.totalCount(), referrers, next.hasMore(),
                next.nextCursor(),
                steps()
                        .nextWhen(next.hasMore(), onObject(REFERRERS_TOOL, self).with(LIMIT, pageSize)
                                .with(CURSOR, next.nextCursor()).why(WHY_NEXT_PAGE))
                        .next(onObject(PATH_TO_ROOT_TOOL, self).why(WHY_PATH))
                        .followUp(),
                objectLink(canonical), NOTE_OBJECT);
        return footed(HeapObjectAnswers.text(answer), answer, answer.followUp(), answer.uiLink(), answer.uiLinkNote());
    }

    @Tool(description = "Names the tables of the heap-dump index database: class (loaded Java classes), "
            + "instance (every live object), outbound_ref (reference graph), gc_root (GC root entries), "
            + "dominator (dominator tree), retained_size (retained heap sizes per instance), string (HPROF "
            + "string pool), dump_metadata (parser and heap-shape metadata). heap_describeTable gives one "
            + "table's columns.")
    @McpOutputSchema(Tables.class)
    @McpToolMeta(cost = McpToolCost.CHEAP, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult listTables() {
        SqlQueryResult result = sql(LIST_TABLES_SQL, LIST_TABLES_ROW_CAP);
        List<String> tables = result.rows().stream().map(row -> row.getFirst()).toList();
        Optional<String> first = tables.stream().findFirst();
        return McpToolResult.of(new Tables(profileId, tables,
                steps()
                        .nextWhen(first.isPresent(), onProfile(DESCRIBE_TABLE_TOOL)
                                .with(TABLE_NAME, first.orElse(null)).why(WHY_DESCRIBE))
                        .followUp()));
    }

    @Tool(description = "Describes one heap-dump table: its column names, types and nullability. "
            + "Conventions: (1) instance.record_kind is an enum: 0=INSTANCE_DUMP, 1=OBJECT_ARRAY, "
            + "2=PRIMITIVE_ARRAY. (2) class.name is stored in dot notation (e.g. "
            + "'java.util.HashMap'), already normalized from the HPROF JNI form. (3) The "
            + "retained_size table is filled only once the dominator tree is built; LEFT JOIN it and "
            + "expect NULLs before that.")
    @McpOutputSchema(TableColumns.class)
    @McpToolMeta(cost = McpToolCost.CHEAP, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult describeTable(
            @ToolParam(required = true, description = "Name of the table to describe (e.g. 'instance', 'class', 'outbound_ref')")
            String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new ToolExecutionException("A table name is required. Call heap_listTables to see them.");
        }
        String table = tableName.trim();
        // The name is compared as a string against information_schema rather than interpolated into a
        // FROM clause, so it can only ever match a table or match nothing.
        String safeName = table.replace("'", "");
        SqlQueryResult result = sql(DESCRIBE_TABLE_SQL.formatted(safeName), DESCRIBE_TABLE_ROW_CAP);
        if (result.rows().isEmpty()) {
            throw new ToolExecutionException("Table '" + table + "' not found. Call heap_listTables to see them.");
        }
        List<Column> columns = result.rows().stream()
                .map(row -> new Column(row.get(0), row.get(1), NULLABLE_YES.equalsIgnoreCase(row.get(2))))
                .toList();
        return McpToolResult.of(new TableColumns(profileId, safeName, columns,
                steps()
                        .next(onProfile(EXECUTE_QUERY_TOOL).with(QUERY, SAMPLE_QUERY.formatted(safeName)).why(WHY_SAMPLE))
                        .followUp()));
    }

    @Tool(description = "Executes a read-only DuckDB SQL query against the heap-dump index database. "
            + "Only SELECT and WITH (CTE) queries are accepted; results are always capped at 100 "
            + "rows, whatever LIMIT the query carries, and run under a 30-second timeout. Each row is an "
            + "array of its cells as text, each cut to " + HeapLimits.VALUE_CHARS + " characters, a SQL NULL as "
            + "null; the rows are fitted to one answer, and capped says rows were left out. "
            + "heap_listTables and heap_describeTable give the schema. Tips: (1) join `instance` "
            + "with `class` on class_id for class names; (2) join `retained_size` on instance_id for "
            + "retained-heap totals; (3) `outbound_ref` (source_id, target_id, field_kind, field_id) "
            + "walks the reference graph; (4) class.name uses dot notation (e.g. "
            + "'java.util.HashMap').")
    @McpOutputSchema(QueryResult.class)
    @McpToolMeta(cost = McpToolCost.EXPENSIVE, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult executeQuery(
            @ToolParam(required = true, description = "DuckDB SQL query (SELECT or WITH) to run against the heap-dump index. "
                    + "At most 100 rows come back whatever LIMIT it carries; aggregate or tighten the WHERE clause "
                    + "rather than paging through a large result.")
            String query) {
        if (query == null || query.isBlank()) {
            throw new ToolExecutionException("Query is required");
        }
        SqlQueryResult result = sql(query, EXECUTE_QUERY_ROW_CAP);
        List<List<String>> rows = result.rows().stream().map(HeapDumpMcpTools::cutCells).toList();
        return McpToolResult.of(FittingPage.largest(rows.size(),
                        shown -> queryResult(result, rows.subList(0, shown), result.capped() || shown < rows.size()),
                        answer -> Json.toString(answer).length() <= McpToolOutput.MAX_CHARS)
                .orElseThrow(() -> new ToolExecutionException(ROW_TOO_WIDE)));
    }

    @Tool(description = "Returns the heap dump's parser and shape metadata: HPROF version, id size (4 "
            + "or 8 bytes), compressed-oops flag, total bytes parsed, record count, warning count, "
            + "parser version and the parse time as UTC epoch milliseconds.")
    @McpOutputSchema(DumpMetadata.class)
    @McpToolMeta(cost = McpToolCost.CHEAP, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult getDumpMetadata() {
        SqlQueryResult result = sql(DUMP_METADATA_SQL, DUMP_METADATA_ROW_CAP);
        McpFollowUp followUp = steps().next(onProfile(SUMMARY_TOOL).why(WHY_SUMMARY)).followUp();
        String link = link(MicroscopeView.HEAP_DUMP_OVERVIEW);
        if (result.rows().isEmpty()) {
            return McpToolResult.of(new DumpMetadata(MetadataStatus.NO_METADATA, NO_METADATA, profileId, null,
                    followUp, link));
        }
        MetadataRow row = new MetadataRow(result.columns(), result.rows().getFirst());
        return McpToolResult.of(new DumpMetadata(MetadataStatus.OK, null, profileId,
                new Metadata(row.integer(ID_SIZE), row.text(HPROF_VERSION), row.flag(COMPRESSED_OOPS),
                        row.number(BYTES_PARSED), row.number(RECORD_COUNT), row.number(WARNING_COUNT),
                        row.flag(TRUNCATED), row.text(PARSER_VERSION), row.number(PARSED_AT_MS)),
                followUp, link));
    }

    private QueryResult queryResult(SqlQueryResult result, List<List<String>> rows, boolean capped) {
        return new QueryResult(profileId, result.columns(), rows, EXECUTE_QUERY_ROW_CAP, capped,
                steps().guidanceWhen(capped, CAPPED_GUIDANCE).followUp());
    }

    /** A row with each cell cut to the value cap; a NULL stays null. */
    private static List<String> cutCells(List<String> row) {
        List<String> cells = new ArrayList<>(row.size());
        for (String cell : row) {
            cells.add(cut(cell, HeapLimits.VALUE_CHARS));
        }
        return cells;
    }

    /** The cells of the one metadata row, read by column name. */
    private record MetadataRow(List<String> columns, List<String> cells) {

        String text(String column) {
            int index = columns.indexOf(column);
            return index < 0 ? null : cells.get(index);
        }

        Integer integer(String column) {
            String value = text(column);
            return value == null ? null : Integer.valueOf(value.trim());
        }

        Long number(String column) {
            String value = text(column);
            return value == null ? null : Long.valueOf(value.trim());
        }

        Boolean flag(String column) {
            String value = text(column);
            return value == null ? null : Boolean.valueOf(value.trim());
        }
    }

    private static Suspect suspect(LeakSuspect suspect) {
        List<Contributor> contributors = listOf(suspect.dominatedHistogram()).stream()
                .limit(HeapLimits.SUSPECT_CONTRIBUTORS)
                .map(entry -> new Contributor(entry.className(), entry.instanceCount(), entry.retainedSize(),
                        entry.percentOfCluster()))
                .toList();
        return new Suspect(suspect.rank(), suspect.className(), HeapObjectIds.formatNullable(suspect.objectId()),
                suspect.retainedSize(), suspect.heapPercentage(), suspect.instanceCount(), suspect.reason(),
                suspect.accumulationPoint(), HeapObjectIds.formatNullable(suspect.accumulationPointId()),
                suspect.accumulationPointClass(), suspect.leakScore(), HeapObjectIds.format(suspect.classLoaderId()),
                suspect.classLoaderClassName(), contributors,
                listOf(suspect.dominatedHistogram()).size() - contributors.size());
    }

    private static LoaderChain chain(ClassLoaderLeakChain chain) {
        List<Hint> hints = listOf(chain.causeHints()).stream()
                .map(hint -> new Hint(hint.kind(), hint.description(), HeapObjectIds.format(hint.objectId())))
                .toList();
        return new LoaderChain(HeapObjectIds.format(chain.classLoaderId()), chain.classLoaderClassName(),
                chain.classCount(), chain.retainedSize(), chain.hasDuplicateClasses(), hints,
                chain.gcRootPath() == null ? null : RootPath.of(chain.gcRootPath()));
    }

    private static HeapObjectAnswers.Instance instance(ClassInstanceEntry entry) {
        Map<String, String> details = new LinkedHashMap<>();
        if (entry.objectParams() != null) {
            entry.objectParams().forEach((name, value) -> {
                if (name != null && value != null) {
                    details.put(name, cut(value, HeapLimits.PARAMETER_CHARS));
                }
            });
        }
        return new HeapObjectAnswers.Instance(HeapObjectIds.format(entry.objectId()), entry.shallowSize(),
                entry.retainedSize(), details);
    }

    private static DominatorEntry dominatorEntry(DominatorNode node) {
        return new DominatorEntry(HeapObjectIds.format(node.objectId()), node.className(), node.fieldName(),
                node.shallowSize(), node.retainedSize(), node.retainedPercent(), node.hasChildren(), node.gcRootKind());
    }

    private static Optional<String> firstWithChildren(List<DominatorEntry> entries) {
        return entries.stream().filter(DominatorEntry::hasChildren).findFirst().map(DominatorEntry::objectId);
    }

    /**
     * How many rows a top-N cap left out, where it can tell: none when the list stopped short of the
     * cap, unknown (null) when it reached it — never a false zero.
     */
    private static Integer omittedBelowCap(int shown, int cap) {
        return shown < cap ? 0 : null;
    }

    /**
     * How many rows of a report's own list an answer left out, where it can tell: unknown (null) when
     * that list is as long as the report ever keeps, since the report may have cut more itself.
     */
    private static Integer omittedBelowEngineCap(int reported, int shown, int engineCap) {
        return reported >= engineCap ? null : reported - shown;
    }

    /**
     * The id a caller passed, checked against the dump: an id no object has is refused as the argument
     * error it is, rather than answered as an object with no fields, referrers or path.
     */
    private long existing(String objectId) {
        long id = HeapObjectIds.parse(objectId);
        if (!read("object", () -> delegate.objectExists(id))) {
            throw new IllegalArgumentException(UNKNOWN_OBJECT.formatted(HeapObjectIds.format(id)));
        }
        return id;
    }

    private static <T> List<T> listOf(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String cut(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private String notRunYet(String report, HeapReport name) {
        return NOT_RUN_YET.formatted(report, name.name());
    }

    private McpFollowUp notRunYetSteps(HeapReport report) {
        return steps()
                .next(onProfile(PREPARE_TOOL).with(REPORT, report).why(WHY_PREPARE))
                .next(onProfile(STATUS_TOOL).why(WHY_STATUS))
                .followUp();
    }

    /** The calls that follow an object answer: why it is reachable and what it retains. */
    private NextSteps.Builder objectSteps(Optional<String> objectId) {
        return steps()
                .nextWhen(objectId.isPresent(), onObject(PATH_TO_ROOT_TOOL, objectId).why(WHY_PATH))
                .nextWhen(objectId.isPresent(), onObject(DOMINATOR_CHILDREN_TOOL, objectId).why(WHY_CHILDREN));
    }

    private NextSteps.Builder steps() {
        return NextSteps.builder(advertised);
    }

    private McpNextTool.Call onProfile(String tool) {
        return McpNextTool.call(tool).with(PROFILE_ID, profileId);
    }

    private McpNextTool.Call onObject(String tool, Optional<String> objectId) {
        return onProfile(tool).with(HeapObjectIds.PARAMETER, objectId.orElse(null));
    }

    private String link(MicroscopeView view) {
        return UiLinks.view(profileId, view);
    }

    /** The GC-root path page, which runs its search for the object the link names. */
    private String objectLink(String objectId) {
        Map<String, String> query = UiLinks.query();
        query.put(HeapObjectIds.PARAMETER, objectId);
        return UiLinks.view(profileId, MicroscopeView.HEAP_DUMP_GC_ROOT_PATH, query);
    }

    /** The fixed-width text, capped with the footer's room reserved, then the footer, beside the record. */
    private static McpToolResult footed(String body, Record answer, McpFollowUp followUp, String uiLink, String note) {
        return McpToolResult.of(LinkedOutput.footed(body, followUp, uiLink, note).text(), answer);
    }

    /** One read of the index, a failure of which is a heap failure naming what was read. */
    private static <T> T read(String what, Supplier<T> read) {
        try {
            return read.get();
        } catch (RuntimeException e) {
            LOG.error("Failed to read the heap dump: what={} message={}", what, e.getMessage(), e);
            throw toolFailure("Failed to get " + what + ": ", e);
        }
    }

    /**
     * Runs one read-only SQL query, turning a refusal into a tool error.
     */
    private SqlQueryResult sql(String sql, int rowCap) {
        try {
            return delegate.executeSql(sql, rowCap);
        } catch (RuntimeException e) {
            throw toolFailure("", e);
        }
    }

    private static ToolExecutionException toolFailure(String prefix, Exception cause) {
        if (cause instanceof ToolExecutionException toolError) {
            return toolError;
        }
        return new ToolExecutionException(prefix + cause.getMessage(), cause);
    }
}
