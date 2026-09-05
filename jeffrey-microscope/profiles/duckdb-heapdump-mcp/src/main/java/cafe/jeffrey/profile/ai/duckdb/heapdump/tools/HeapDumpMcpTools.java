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

package cafe.jeffrey.profile.ai.duckdb.heapdump.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import cafe.jeffrey.profile.mcp.ToolParamValues;
import cafe.jeffrey.profile.heapdump.model.*;
import cafe.jeffrey.profile.heapdump.view.SqlQueryResult;
import cafe.jeffrey.shared.common.BytesUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Heap dump tools for AI-powered heap analysis.
 * Provides methods that can be called by AI models to explore and analyze Java heap dumps.
 */
public class HeapDumpMcpTools {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpMcpTools.class);

    private static final int MAX_RESULT_LENGTH = 50000;

    /** Row caps applied per-tool when calling delegate.executeQuery — match the same MAX_QUERY_LIMIT the manager enforces. */
    /** How wide the rule under a table header may get, however long the header is. */
    /**
     * What a report that was never computed says. It names the tool that computes it rather than
     * sending the reader to the browser: over MCP the UI may not even be reachable, and a dead end
     * that offers no next call is where a heap session stops.
     */
    private static final String NOT_RUN_YET =
            "%s has not been run for this heap dump yet. Call heap_prepare with report '%s' to compute "
                    + "it, then read this tool again — heap_status reports progress meanwhile. It can "
                    + "also be run from the matching page in the Jeffrey UI.";

    private static final int MAX_HEADER_RULE = 120;

    private static final int LIST_TABLES_ROW_CAP = 100;
    private static final int DESCRIBE_TABLE_ROW_CAP = 200;
    private static final int EXECUTE_QUERY_ROW_CAP = 100;
    private static final int DUMP_METADATA_ROW_CAP = 1;

    private final HeapDumpToolsDelegate delegate;

    public HeapDumpMcpTools(HeapDumpToolsDelegate delegate) {
        this.delegate = delegate;
    }

    @Tool(description = "Get heap dump summary statistics including total live bytes, total live instances, " +
            "number of classes, and number of GC roots. Use this first to understand the overall heap state.")
    public String getHeapSummary() {
        try {
            HeapSummary summary = delegate.getSummary();
            return """
                    Heap Summary:

                    Total Live Bytes:      %s (%,d bytes)
                    Total Live Instances:  %,d
                    Number of Classes:     %,d
                    Number of GC Roots:    %,d
                    Heap Dump Time:        %s
                    """.formatted(
                    BytesUtils.format(summary.totalBytes()),
                    summary.totalBytes(),
                    summary.totalInstances(),
                    summary.classCount(),
                    summary.gcRootCount(),
                    summary.timestamp() != null ? summary.timestamp().toString() : "unknown"
            );
        } catch (Exception e) {
            LOG.error("Failed to get heap summary: message={}", e.getMessage(), e);
            return "Error: Failed to get heap summary: " + e.getMessage();
        }
    }

    @Tool(description = "Get class histogram showing top classes by memory usage or instance count. " +
            "Returns class name, instance count, and total size for each class.")
    public String getClassHistogram(
            @ToolParam(required = false, description = "Number of top classes to return (default: 50, max: 200)")
            Integer topN,
            @ToolParam(required = false, description = "Sort criteria: SIZE (default) or COUNT")
            @ToolParamValues({"SIZE", "COUNT"})
            String sortBy) {
        try {
            int effectiveTopN = topN != null ? Math.min(Math.max(1, topN), 200) : 50;
            SortBy effectiveSortBy = "COUNT".equalsIgnoreCase(sortBy) ? SortBy.COUNT : SortBy.SIZE;

            List<ClassHistogramEntry> entries = delegate.getClassHistogram(effectiveTopN, effectiveSortBy);

            StringBuilder result = new StringBuilder();
            result.append("Class Histogram (top ").append(effectiveTopN).append(" by ")
                    .append(effectiveSortBy).append("):\n\n");
            result.append(String.format("%-60s %15s %15s%n", "CLASS", "INSTANCES", "TOTAL SIZE"));
            result.append("-".repeat(92)).append("\n");

            for (ClassHistogramEntry entry : entries) {
                if (result.length() > MAX_RESULT_LENGTH) {
                    result.append("\n... (output truncated)");
                    break;
                }
                result.append(String.format("%-60s %,15d %15s%n",
                        truncate(entry.className(), 60),
                        entry.instanceCount(),
                        BytesUtils.format(entry.totalSize())));
            }

            result.append("\n").append(entries.size()).append(" class(es) returned");
            return withNextSteps(result.toString(), STEP_INSTANCES, STEP_GC_ROOT_PATH);
        } catch (Exception e) {
            LOG.error("Failed to get class histogram: message={}", e.getMessage(), e);
            return "Error: Failed to get class histogram: " + e.getMessage();
        }
    }

    @Tool(description = "Get the biggest individual objects in the heap by retained size. " +
            "This helps identify which single objects hold the most memory. " +
            "Note: This analysis may need to be run first if results don't exist yet.")
    public String getBiggestObjects(
            @ToolParam(required = false, description = "Number of biggest objects to return (default: 20, max: 50)")
            Integer topN) {
        try {
            int effectiveTopN = topN != null ? Math.min(Math.max(1, topN), 50) : 20;

            BiggestObjectsReport report = delegate.getBiggestObjects(effectiveTopN);
            if (report == null) {
                return NOT_RUN_YET.formatted("Biggest objects analysis", "biggest");
            }

            StringBuilder result = new StringBuilder();
            result.append("Biggest Objects Report:\n\n");
            result.append("Total Heap Size: ").append(BytesUtils.format(report.totalHeapSize())).append("\n");
            result.append("Total Retained by Top Objects: ").append(BytesUtils.format(report.totalRetainedSize())).append("\n\n");
            result.append(String.format("%-50s %15s %15s %12s%n", "CLASS", "SHALLOW SIZE", "RETAINED SIZE", "OBJECT ID"));
            result.append("-".repeat(95)).append("\n");

            for (BiggestObjectEntry entry : report.entries()) {
                if (result.length() > MAX_RESULT_LENGTH) {
                    result.append("\n... (output truncated)");
                    break;
                }
                result.append(String.format("%-50s %15s %15s %12d%n",
                        truncate(entry.className(), 50),
                        BytesUtils.format(entry.shallowSize()),
                        BytesUtils.format(entry.retainedSize()),
                        entry.objectId()));
            }

            return withNextSteps(result.toString(), STEP_GC_ROOT_PATH, STEP_DOMINATOR_FIRST);
        } catch (Exception e) {
            LOG.error("Failed to get biggest objects: message={}", e.getMessage(), e);
            return "Error: Failed to get biggest objects: " + e.getMessage();
        }
    }

    @Tool(description = "Get leak suspect analysis results. Identifies potential memory leak suspects " +
            "using heuristics like single objects with disproportionate retained size, " +
            "or classes with many instances collectively holding significant memory. " +
            "Note: This analysis may need to be run first if results don't exist yet.")
    public String getLeakSuspects() {
        try {
            LeakSuspectsReport report = delegate.getLeakSuspects();
            if (report == null) {
                return NOT_RUN_YET.formatted("Leak suspects analysis", "leaks");
            }

            StringBuilder result = new StringBuilder();
            result.append("Leak Suspects Report:\n\n");
            result.append("Total Heap Size: ").append(BytesUtils.format(report.totalHeapSize())).append("\n");
            result.append("Analyzed Bytes: ").append(BytesUtils.format(report.analyzedBytes())).append("\n\n");

            if (report.suspects().isEmpty()) {
                result.append("No leak suspects identified.");
                return result.toString();
            }

            for (LeakSuspect suspect : report.suspects()) {
                result.append("Suspect #").append(suspect.rank()).append(": ").append(suspect.className()).append("\n");
                result.append("  Reason: ").append(suspect.reason()).append("\n");
                result.append("  Accumulation Point: ").append(suspect.accumulationPoint()).append("\n");
                if (suspect.accumulationPointId() != null) {
                    result.append("  Accumulation Point Object ID: ").append(suspect.accumulationPointId())
                            .append(" (").append(suspect.accumulationPointClass()).append(")\n");
                }
                result.append("  Retained Size: ").append(BytesUtils.format(suspect.retainedSize()))
                        .append(" (").append(String.format("%.1f%%", suspect.heapPercentage())).append(")\n");
                result.append("  Leak Score: ").append(String.format("%.1f", suspect.leakScore())).append("\n");
                result.append("  Instance Count: ").append(String.format("%,d", suspect.instanceCount())).append("\n");
                result.append("  Class Loader: ").append(suspect.classLoaderClassName())
                        .append(" (id=").append(suspect.classLoaderId()).append(")\n");
                if (suspect.objectId() != null) {
                    result.append("  Cluster Root Object ID: ").append(suspect.objectId()).append("\n");
                }
                if (suspect.dominatedHistogram() != null && !suspect.dominatedHistogram().isEmpty()) {
                    result.append("  Top Contributing Classes:\n");
                    int shown = 0;
                    for (DominatedClassEntry entry : suspect.dominatedHistogram()) {
                        if (shown++ >= 5) {
                            break;
                        }
                        result.append("    - ").append(entry.className())
                                .append(": ").append(String.format("%,d", entry.instanceCount())).append(" instances, ")
                                .append(BytesUtils.format(entry.retainedSize()))
                                .append(" (").append(String.format("%.1f%%", entry.percentOfCluster())).append(" of cluster)\n");
                    }
                }
                result.append("\n");
            }

            if (report.topLeakingClassLoaders() != null && !report.topLeakingClassLoaders().isEmpty()) {
                result.append("Top Leaking Class Loaders (across all suspects):\n");
                for (ClassLoaderLeakSummary loader : report.topLeakingClassLoaders()) {
                    result.append("  - ").append(loader.classLoaderClassName())
                            .append(": ").append(BytesUtils.format(loader.totalRetainedSize()))
                            .append(" across ").append(loader.suspectCount()).append(" suspect(s)\n");
                }
            }

            return withNextSteps(result.toString(), STEP_GC_ROOT_PATH, STEP_DOMINATOR_FIRST);
        } catch (Exception e) {
            LOG.error("Failed to get leak suspects: message={}", e.getMessage(), e);
            return "Error: Failed to get leak suspects: " + e.getMessage();
        }
    }

    @Tool(description = "Get class-loader leak chains: for each suspicious class loader (large retained " +
            "size, in duplicate-classes, or webapp/URL loader), shows the GC-root path keeping it alive " +
            "and any matched leak-pattern hints (ThreadLocal, JDBC driver, JNI global, ServiceLoader, " +
            "static Logger, contextClassLoader). The canonical Tomcat-redeploy diagnostic. " +
            "Note: This requires the class loader analysis to have been run first.")
    public String getClassLoaderLeakChains() {
        try {
            ClassLoaderReport report = delegate.getClassLoaderAnalysis();
            if (report == null) {
                return NOT_RUN_YET.formatted("Class loader analysis", "classloaders");
            }
            List<ClassLoaderLeakChain> chains = report.leakChains();
            if (chains == null || chains.isEmpty()) {
                return "No suspicious class loaders detected.";
            }
            StringBuilder result = new StringBuilder();
            result.append("Class Loader Leak Chains:\n\n");
            for (ClassLoaderLeakChain chain : chains) {
                result.append(chain.classLoaderClassName())
                        .append(" (id=").append(chain.classLoaderId()).append(")\n");
                result.append("  Retained: ").append(BytesUtils.format(chain.retainedSize()))
                        .append(", classes: ").append(chain.classCount()).append("\n");
                if (chain.hasDuplicateClasses()) {
                    result.append("  ! At least one class is also loaded by another loader (duplicate)\n");
                }
                if (!chain.causeHints().isEmpty()) {
                    result.append("  Cause hints: ");
                    for (CauseHint hint : chain.causeHints()) {
                        result.append(hint.kind()).append("(").append(hint.description()).append(") ");
                    }
                    result.append("\n");
                }
                if (chain.gcRootPath() != null) {
                    result.append("  GC root: ").append(chain.gcRootPath().rootType())
                            .append(" — ").append(chain.gcRootPath().rootClassName()).append("\n");
                    result.append("  Path: ").append(chain.gcRootPath().steps().size()).append(" hop(s)\n");
                } else {
                    result.append("  No GC-root path computed\n");
                }
                result.append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get class-loader leak chains: message={}", e.getMessage(), e);
            return "Error: Failed to get class-loader leak chains: " + e.getMessage();
        }
    }

    @Tool(description = "Get the top memory consumers grouped by (package, class loader). Answers " +
            "'which subsystem is the most bloated?'. Also includes a per-package component report. " +
            "Note: This requires the consumer report to have been run first.")
    public String getTopConsumers() {
        try {
            ConsumerReport report = delegate.getConsumerReport();
            if (report == null) {
                return NOT_RUN_YET.formatted("Consumer report", "consumers");
            }
            StringBuilder result = new StringBuilder();
            result.append("Top Consumers (by package + class loader):\n");
            int shown = 0;
            for (ConsumerEntry entry : report.topConsumers()) {
                if (shown++ >= 20) {
                    break;
                }
                result.append("  ").append(entry.packageName())
                        .append(" [").append(entry.classLoaderClassName()).append("]")
                        .append(" — ").append(BytesUtils.format(entry.retainedSize()))
                        .append(", ").append(entry.classCount()).append(" classes / ")
                        .append(String.format("%,d", entry.instanceCount())).append(" instances\n");
            }
            result.append("\nComponent Report (per-package):\n");
            shown = 0;
            for (ComponentEntry entry : report.componentReport()) {
                if (shown++ >= 20) {
                    break;
                }
                result.append("  ").append(entry.packageName())
                        .append(" — ").append(BytesUtils.format(entry.retainedSize()))
                        .append(", ").append(entry.classCount()).append(" classes\n");
            }
            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get top consumers: message={}", e.getMessage(), e);
            return "Error: Failed to get top consumers: " + e.getMessage();
        }
    }

    @Tool(description = "Get string analysis results showing duplicate strings, longest strings, " +
            "and potential memory waste from string duplication. " +
            "Note: This analysis may need to be run first if results don't exist yet.")
    public String getStringAnalysis() {
        try {
            StringAnalysisReport report = delegate.getStringAnalysis();
            if (report == null) {
                return NOT_RUN_YET.formatted("String analysis", "strings");
            }

            StringBuilder result = new StringBuilder();
            result.append("String Analysis Report:\n\n");
            result.append("Total Strings: ").append(String.format("%,d", report.totalStrings())).append("\n");
            result.append("Total String Shallow Size: ").append(BytesUtils.format(report.totalStringShallowSize())).append("\n");
            result.append("Unique Arrays: ").append(String.format("%,d", report.uniqueArrays())).append("\n");
            result.append("Shared Arrays: ").append(String.format("%,d", report.sharedArrays())).append("\n");
            result.append("Memory Saved by Dedup: ").append(BytesUtils.format(report.memorySavedByDedup())).append("\n");
            result.append("Potential Savings: ").append(BytesUtils.format(report.potentialSavings())).append("\n\n");

            if (report.opportunities() != null && !report.opportunities().isEmpty()) {
                result.append("Top Deduplication Opportunities:\n");
                result.append(String.format("%-50s %10s %15s%n", "CONTENT", "COUNT", "SAVINGS"));
                result.append("-".repeat(77)).append("\n");

                int count = 0;
                for (var entry : report.opportunities()) {
                    if (count >= 20 || result.length() > MAX_RESULT_LENGTH) {
                        break;
                    }
                    result.append(String.format("%-50s %,10d %15s%n",
                            truncate(entry.content(), 50),
                            entry.count(),
                            BytesUtils.format(entry.savings())));
                    count++;
                }
            }

            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get string analysis: message={}", e.getMessage(), e);
            return "Error: Failed to get string analysis: " + e.getMessage();
        }
    }

    @Tool(description = "Get collection analysis results showing empty, singleton, and oversized collections " +
            "(HashMap, ArrayList, HashSet, etc.) with their fill ratios. " +
            "Note: This analysis may need to be run first if results don't exist yet.")
    public String getCollectionAnalysis() {
        try {
            CollectionAnalysisReport report = delegate.getCollectionAnalysis();
            if (report == null) {
                return NOT_RUN_YET.formatted("Collection analysis", "collections");
            }

            StringBuilder result = new StringBuilder();
            result.append("Collection Analysis Report:\n\n");
            result.append("Total Collections: ").append(String.format("%,d", report.totalCollections())).append("\n");
            result.append("Total Empty: ").append(String.format("%,d", report.totalEmptyCount())).append("\n");
            result.append("Total Wasted Bytes: ").append(BytesUtils.format(report.totalWastedBytes())).append("\n\n");

            if (report.byType() != null) {
                for (var collection : report.byType()) {
                    result.append("Collection Type: ").append(collection.collectionType()).append("\n");
                    result.append("  Total Count: ").append(String.format("%,d", collection.totalCount())).append("\n");
                    result.append("  Empty: ").append(String.format("%,d", collection.emptyCount())).append("\n");
                    result.append("  Wasted Bytes: ").append(BytesUtils.format(collection.totalWastedBytes())).append("\n");
                    result.append("  Avg Fill Ratio: ").append(String.format("%.2f", collection.avgFillRatio())).append("\n\n");
                }
            }

            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get collection analysis: message={}", e.getMessage(), e);
            return "Error: Failed to get collection analysis: " + e.getMessage();
        }
    }

    @Tool(description = "Get thread information from the heap dump including thread names and object counts.")
    public String getThreads() {
        try {
            List<HeapThreadInfo> threads = delegate.getThreads();

            StringBuilder result = new StringBuilder();
            result.append("Threads in Heap Dump:\n\n");
            result.append(String.format("%-50s %15s%n", "THREAD NAME", "OBJECT ID"));
            result.append("-".repeat(67)).append("\n");

            for (HeapThreadInfo thread : threads) {
                if (result.length() > MAX_RESULT_LENGTH) {
                    result.append("\n... (output truncated)");
                    break;
                }
                result.append(String.format("%-50s %15d%n",
                        truncate(thread.name(), 50),
                        thread.objectId()));
            }

            result.append("\n").append(threads.size()).append(" thread(s) found");
            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get threads: message={}", e.getMessage(), e);
            return "Error: Failed to get threads: " + e.getMessage();
        }
    }

    @Tool(description = "Get GC root summary showing the types and counts of GC roots in the heap.")
    public String getGCRootSummary() {
        try {
            GCRootSummary summary = delegate.getGCRootSummary();

            StringBuilder result = new StringBuilder();
            result.append("GC Root Summary:\n\n");
            result.append("Total GC Roots: ").append(String.format("%,d", summary.totalRoots())).append("\n\n");
            result.append(String.format("%-40s %15s%n", "ROOT TYPE", "COUNT"));
            result.append("-".repeat(57)).append("\n");

            for (Map.Entry<String, Long> entry : summary.rootsByType().entrySet()) {
                result.append(String.format("%-40s %,15d%n", entry.getKey(), entry.getValue()));
            }

            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get GC root summary: message={}", e.getMessage(), e);
            return "Error: Failed to get GC root summary: " + e.getMessage();
        }
    }

    @Tool(description = "Browse instances of a specific class. Returns a paginated list of instances " +
            "with their object IDs and shallow sizes. Use this to explore specific classes found in the histogram.")
    public String browseClassInstances(
            @ToolParam(required = true, description = "Fully qualified class name (e.g., 'java.lang.String', 'java.util.HashMap')")
            String className,
            @ToolParam(required = false, description = "Maximum number of instances to return (default: 20, max: 50)")
            Integer limit,
            @ToolParam(required = false, description = "Offset for pagination (default: 0)")
            Integer offset) {
        try {
            if (className == null || className.isBlank()) {
                return "Error: Class name is required";
            }

            int effectiveLimit = limit != null ? Math.min(Math.max(1, limit), 50) : 20;
            int effectiveOffset = offset != null ? Math.max(0, offset) : 0;

            ClassInstancesResponse response = delegate.getClassInstances(className, effectiveLimit, effectiveOffset, false);

            StringBuilder result = new StringBuilder();
            result.append("Instances of ").append(className).append(":\n\n");
            result.append("Total Instances: ").append(String.format("%,d", response.totalInstances())).append("\n");
            result.append("Showing: ").append(effectiveOffset + 1).append("-")
                    .append(effectiveOffset + response.instances().size()).append("\n\n");
            result.append(String.format("%-15s %-50s %15s%n", "OBJECT ID", "DETAILS", "SHALLOW SIZE"));
            result.append("-".repeat(82)).append("\n");

            for (var instance : response.instances()) {
                if (result.length() > MAX_RESULT_LENGTH) {
                    result.append("\n... (output truncated)");
                    break;
                }
                String details = instance.objectParams() != null ? instance.objectParams().toString() : "";
                result.append(String.format("%-15d %-50s %15s%n",
                        instance.objectId(),
                        truncate(details, 50),
                        BytesUtils.format(instance.shallowSize())));
            }

            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to browse class instances: className={} message={}", className, e.getMessage(), e);
            return "Error: Failed to browse class instances: " + e.getMessage();
        }
    }

    @Tool(description = "Get detailed information about a specific object instance including all its fields and values. " +
            "Use this to inspect individual objects found via class instance browsing or other analyses.")
    public String getInstanceDetail(
            @ToolParam(required = true, description = "The object ID of the instance to inspect")
            Long objectId) {
        try {
            InstanceDetail detail = delegate.getInstanceDetail(requireObjectId(objectId), false);
            if (detail == null) {
                return "Error: Instance not found for object ID: " + objectId;
            }

            StringBuilder result = new StringBuilder();
            result.append("Instance Detail (Object ID: ").append(objectId).append("):\n\n");
            result.append("Class: ").append(detail.className()).append("\n");
            result.append("Shallow Size: ").append(BytesUtils.format(detail.shallowSize())).append("\n\n");

            if (detail.fields() != null && !detail.fields().isEmpty()) {
                result.append("Fields:\n");
                result.append(String.format("%-30s %-30s %s%n", "NAME", "TYPE", "VALUE"));
                result.append("-".repeat(90)).append("\n");

                for (var field : detail.fields()) {
                    if (result.length() > MAX_RESULT_LENGTH) {
                        result.append("\n... (output truncated)");
                        break;
                    }
                    result.append(String.format("%-30s %-30s %s%n",
                            truncate(field.name(), 30),
                            truncate(field.type(), 30),
                            truncate(field.value() != null ? field.value() : "null", 60)));
                }
            }

            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get instance detail: objectId={} message={}", objectId, e.getMessage(), e);
            return "Error: Failed to get instance detail: " + e.getMessage();
        }
    }

    @Tool(description = "Get the dominator tree roots - the objects with the largest retained size in the heap. " +
            "The dominator tree shows which objects are responsible for keeping other objects alive.")
    public String getDominatorTreeRoots(
            @ToolParam(required = false, description = "Maximum number of root entries to return (default: 20, max: 50)")
            Integer limit) {
        try {
            int effectiveLimit = limit != null ? Math.min(Math.max(1, limit), 50) : 20;

            DominatorTreeResponse response = delegate.getDominatorTreeRoots(effectiveLimit);

            StringBuilder result = new StringBuilder();
            result.append("Dominator Tree Roots (top retained size holders):\n\n");
            result.append(String.format("%-50s %15s %15s %12s%n", "CLASS", "SHALLOW SIZE", "RETAINED SIZE", "OBJECT ID"));
            result.append("-".repeat(95)).append("\n");

            for (var node : response.nodes()) {
                if (result.length() > MAX_RESULT_LENGTH) {
                    result.append("\n... (output truncated)");
                    break;
                }
                result.append(String.format("%-50s %15s %15s %12d%n",
                        truncate(node.className(), 50),
                        BytesUtils.format(node.shallowSize()),
                        BytesUtils.format(node.retainedSize()),
                        node.objectId()));
            }

            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get dominator tree roots: message={}", e.getMessage(), e);
            return "Error: Failed to get dominator tree roots: " + e.getMessage();
        }
    }

    @Tool(description = "Get children of a dominator tree node - objects retained by the given object. " +
            "Use this to drill down into the dominator tree from a root entry.")
    public String getDominatorTreeChildren(
            @ToolParam(required = true, description = "Object ID of the parent node in the dominator tree")
            Long objectId,
            @ToolParam(required = false, description = "Maximum number of children to return (default: 20, max: 50)")
            Integer limit) {
        try {
            int effectiveLimit = limit != null ? Math.min(Math.max(1, limit), 50) : 20;

            DominatorTreeResponse response = delegate.getDominatorTreeChildren(requireObjectId(objectId), effectiveLimit);

            StringBuilder result = new StringBuilder();
            result.append("Dominator Tree Children of Object ID ").append(objectId).append(":\n\n");
            result.append(String.format("%-50s %15s %15s %12s%n", "CLASS", "SHALLOW SIZE", "RETAINED SIZE", "OBJECT ID"));
            result.append("-".repeat(95)).append("\n");

            for (var node : response.nodes()) {
                if (result.length() > MAX_RESULT_LENGTH) {
                    result.append("\n... (output truncated)");
                    break;
                }
                result.append(String.format("%-50s %15s %15s %12d%n",
                        truncate(node.className(), 50),
                        BytesUtils.format(node.shallowSize()),
                        BytesUtils.format(node.retainedSize()),
                        node.objectId()));
            }

            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get dominator tree children: objectId={} message={}", objectId, e.getMessage(), e);
            return "Error: Failed to get dominator tree children: " + e.getMessage();
        }
    }

    @Tool(description = "Find the shortest reference chain(s) from GC roots to a given object. " +
            "This shows why an object is kept alive and cannot be garbage collected. " +
            "Essential for memory leak analysis.")
    public String getPathToGCRoot(
            @ToolParam(required = true, description = "Object ID of the target object")
            Long objectId,
            @ToolParam(required = false, description = "Maximum number of paths to return (default: 3, max: 5)")
            Integer maxPaths) {
        try {
            int effectiveMaxPaths = maxPaths != null ? Math.min(Math.max(1, maxPaths), 5) : 3;

            List<GCRootPath> paths = delegate.getPathsToGCRoot(requireObjectId(objectId), true, effectiveMaxPaths);

            if (paths.isEmpty()) {
                return "No paths to GC root found for object ID: " + objectId;
            }

            StringBuilder result = new StringBuilder();
            result.append("Paths to GC Root for Object ID ").append(objectId).append(":\n\n");

            for (int i = 0; i < paths.size(); i++) {
                GCRootPath path = paths.get(i);
                result.append("Path #").append(i + 1).append(":\n");
                result.append("  GC Root Type: ").append(path.rootType()).append("\n");
                if (path.threadName() != null) {
                    result.append("  Thread: ").append(path.threadName()).append("\n");
                }
                if (path.stackFrame() != null) {
                    result.append("  Stack Frame: ").append(path.stackFrame()).append("\n");
                }

                if (path.steps() != null) {
                    for (var step : path.steps()) {
                        result.append("  -> ").append(step.className());
                        if (step.fieldName() != null) {
                            result.append(".").append(step.fieldName());
                        }
                        result.append(" (ID: ").append(step.objectId()).append(")\n");
                    }
                }
                result.append("\n");
            }

            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get path to GC root: objectId={} message={}", objectId, e.getMessage(), e);
            return "Error: Failed to get path to GC root: " + e.getMessage();
        }
    }

    @Tool(description = "Get objects that reference a given object (referrers/incoming references). " +
            "Use this to understand what keeps an object alive.")
    public String getReferrers(
            @ToolParam(required = true, description = "Object ID to find referrers for")
            Long objectId,
            @ToolParam(required = false, description = "Maximum number of referrers to return (default: 20, max: 50)")
            Integer limit) {
        try {
            int effectiveLimit = limit != null ? Math.min(Math.max(1, limit), 50) : 20;

            InstanceTreeResponse response = delegate.getReferrers(requireObjectId(objectId), effectiveLimit, 0);

            StringBuilder result = new StringBuilder();
            result.append("Referrers of Object ID ").append(objectId).append(":\n\n");
            result.append(String.format("%-50s %15s %12s %s%n", "CLASS", "SIZE", "OBJECT ID", "FIELD"));
            result.append("-".repeat(95)).append("\n");

            for (var node : response.children()) {
                if (result.length() > MAX_RESULT_LENGTH) {
                    result.append("\n... (output truncated)");
                    break;
                }
                result.append(String.format("%-50s %15s %12d %s%n",
                        truncate(node.className(), 50),
                        BytesUtils.format(node.shallowSize()),
                        node.objectId(),
                        node.fieldName() != null ? node.fieldName() : ""));
            }

            result.append("\n").append(response.children().size()).append(" referrer(s) returned");
            if (response.hasMore()) {
                result.append(" (more available)");
            }
            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get referrers: objectId={} message={}", objectId, e.getMessage(), e);
            return "Error: Failed to get referrers: " + e.getMessage();
        }
    }

    @Tool(description = "List all tables in the heap-dump index database. The schema includes: "
            + "class (loaded Java classes), instance (every live object), outbound_ref (reference graph), "
            + "gc_root (GC root entries), dominator (dominator tree), retained_size (retained heap sizes per instance), "
            + "string (HPROF string pool), dump_metadata (parser + heap-shape metadata). "
            + "Use heap_describeTable to get column types for a specific table.")
    public String listTables() {
        return sqlAnswer(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'main' AND table_name NOT LIKE 'flyway_%' "
                        + "ORDER BY table_name",
                LIST_TABLES_ROW_CAP,
                "Tables in the heap-dump index database",
                "\nUse heap_describeTable to get column types of a specific table.");
    }

    @Tool(description = "Get the schema of a specific heap-dump table including column names, types, and nullability. "
            + "Use this before querying to understand table structure. "
            + "Key conventions: (1) instance.record_kind is an enum: 0=INSTANCE_DUMP, 1=OBJECT_ARRAY, 2=PRIMITIVE_ARRAY. "
            + "(2) class.name is stored in dot-notation (e.g. 'java.util.HashMap'), already normalized from HPROF JNI form. "
            + "(3) The retained_size table is populated only after the dominator tree has been built; LEFT JOIN it and "
            + "expect NULLs on heaps where dominator-tree analysis hasn't run yet.")
    public String describeTable(
            @ToolParam(required = true, description = "Name of the table to describe (e.g. 'instance', 'class', 'outbound_ref')")
            String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return "Error: A table name is required. Call heap_listTables to see them.";
        }
        // The name is compared as a string against information_schema rather than interpolated into a
        // FROM clause, so it can only ever match a table or match nothing.
        String safeName = tableName.replace("'", "");
        SqlQueryResult result;
        try {
            result = delegate.executeSql(
                    "SELECT column_name, data_type, is_nullable "
                            + "FROM information_schema.columns "
                            + "WHERE table_schema = 'main' AND table_name = '" + safeName + "' "
                            + "ORDER BY ordinal_position",
                    DESCRIBE_TABLE_ROW_CAP);
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
        if (result.rows().isEmpty()) {
            return "Error: Table '" + tableName + "' not found. Call heap_listTables to see them.";
        }
        return render(result, "Schema for table '" + tableName + "'", null);
    }

    @Tool(description = "Execute a read-only DuckDB SQL query against the heap-dump index database. "
            + "Only SELECT and WITH (CTE) queries are accepted; results are capped at 100 rows and a 30-second timeout. "
            + "Use heap_listTables and heap_describeTable first to learn the schema. "
            + "Tips: (1) join `instance` with `class` on class_id to get class names; "
            + "(2) join with `retained_size` on instance_id for retained-heap totals; "
            + "(3) use `outbound_ref` (source_id, target_id, field_kind, field_id) to walk the reference graph; "
            + "(4) class.name uses dot-notation (e.g. 'java.util.HashMap').")
    public String executeQuery(
            @ToolParam(required = true, description = "DuckDB SQL query (SELECT or WITH) to run against the heap-dump index. "
                    + "Add an explicit LIMIT to control how much you fetch; an implicit 100-row cap is applied otherwise.")
            String query) {
        if (query == null || query.isBlank()) {
            return "Error: Query is required";
        }
        return sqlAnswer(query, EXECUTE_QUERY_ROW_CAP, "Query result", null);
    }

    @Tool(description = "Get the heap-dump's high-level parser/shape metadata: HPROF version, id size (4 or 8 bytes), "
            + "compressed-oops flag, total bytes parsed, record count, warning count, parser version, and parse timestamp. "
            + "Call this once at the start of analysis to orient yourself.")
    public String getDumpMetadata() {
        return sqlAnswer(
                "SELECT id_size, hprof_version, compressed_oops, bytes_parsed, record_count, "
                        + "warning_count, truncated, parser_version, parsed_at_ms FROM dump_metadata",
                DUMP_METADATA_ROW_CAP,
                "Heap-dump metadata",
                null);
    }

    private String truncate(String s, int maxLength) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength - 3) + "...";
    }

    /**
     * Where the next answer lives, appended to a report.
     * <p>
     * These reports name an object or a class and stop; the tool that turns that observation into a
     * cause is a separate call, and the description saying so was read many turns earlier. The lines
     * route and never diagnose - none of them claims the figures above are bad.
     */
    private static String withNextSteps(String body, String... steps) {
        StringBuilder builder = new StringBuilder(body)
                .append("\nWhere to go next:");
        for (String step : steps) {
            builder.append("\n- ").append(step);
        }
        return builder.toString();
    }

    private static final String STEP_GC_ROOT_PATH =
            "A size is an observation, not a cause. heap_getPathToGCRoot on an object id says why "
                    + "those instances are still reachable, which is what makes a leak claim checkable.";
    private static final String STEP_DOMINATOR_FIRST =
            "Retained sizes and the dominator tree are built lazily: run heap_getDominatorTreeRoots "
                    + "once before ranking by retained size, or every retained figure is missing rather "
                    + "than zero.";
    private static final String STEP_INSTANCES =
            "heap_browseClassInstances lists the individual instances of a class, and "
                    + "heap_getInstanceDetail opens one of them.";

    /**
     * An object id the caller actually supplied.
     * <p>
     * The delegate takes a primitive, so an absent id would unbox to zero and quietly inspect whichever
     * object happens to be numbered nought rather than saying that nothing was asked for.
     */
    private static long requireObjectId(Long objectId) {
        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId is required. Object ids come from heap_getClassHistogram, "
                            + "heap_browseClassInstances or heap_getDominatorTreeRoots.");
        }
        return objectId;
    }


    /**
     * Runs one read-only SQL query and renders it, turning a refusal into a sentence.
     */
    private String sqlAnswer(String sql, int rowCap, String title, String footer) {
        try {
            return render(delegate.executeSql(sql, rowCap), title, footer);
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * A result as a delimited table, and it says when it was capped.
     * <p>
     * A capped result that reads like a complete one is the failure this guards against: the model
     * reports the visible rows as the whole answer, and nothing in the text contradicts it.
     */
    private static String render(SqlQueryResult result, String title, String footer) {
        if (result.rows().isEmpty()) {
            return title + ": no rows.";
        }
        String header = String.join(" | ", result.columns());
        StringBuilder sb = new StringBuilder(title)
                .append(" (").append(result.rows().size()).append(" row(s)");
        if (result.capped()) {
            sb.append(", row cap reached \u2014 tighten the WHERE clause or add a LIMIT");
        }
        sb.append("):\n\n").append(header).append("\n")
                .append("-".repeat(Math.min(MAX_HEADER_RULE, header.length()))).append("\n");
        for (List<String> row : result.rows()) {
            if (sb.length() > MAX_RESULT_LENGTH) {
                sb.append("\n... (output truncated)");
                break;
            }
            List<String> cells = new ArrayList<>(row.size());
            for (String value : row) {
                cells.add(value == null ? "NULL" : value);
            }
            sb.append(String.join(" | ", cells)).append("\n");
        }
        if (footer != null) {
            sb.append(footer);
        }
        return sb.toString();
    }

}
