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

package pbouda.jeffrey.profile.ai.heapmcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import pbouda.jeffrey.profile.heapdump.model.*;
import pbouda.jeffrey.shared.common.BytesUtils;

import java.util.List;
import java.util.Map;

/**
 * Heap dump tools for AI-powered heap analysis.
 * Provides methods that can be called by AI models to explore and analyze Java heap dumps.
 */
public class HeapDumpMcpTools {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpMcpTools.class);

    private static final int MAX_RESULT_LENGTH = 50000;

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
            @ToolParam(description = "Number of top classes to return (default: 50, max: 200)")
            Integer topN,
            @ToolParam(description = "Sort criteria: SIZE (default) or COUNT")
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
            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get class histogram: message={}", e.getMessage(), e);
            return "Error: Failed to get class histogram: " + e.getMessage();
        }
    }

    @Tool(description = "Get the biggest individual objects in the heap by retained size. " +
            "This helps identify which single objects hold the most memory. " +
            "Note: This analysis may need to be run first if results don't exist yet.")
    public String getBiggestObjects(
            @ToolParam(description = "Number of biggest objects to return (default: 20, max: 50)")
            Integer topN) {
        try {
            int effectiveTopN = topN != null ? Math.min(Math.max(1, topN), 50) : 20;

            BiggestObjectsReport report = delegate.getBiggestObjects(effectiveTopN);
            if (report == null) {
                return "Biggest objects analysis has not been run yet. The user needs to run it from the UI first.";
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

            return result.toString();
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
                return "Leak suspects analysis has not been run yet. The user needs to run it from the UI first.";
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
                result.append("  Retained Size: ").append(BytesUtils.format(suspect.retainedSize()))
                        .append(" (").append(String.format("%.1f%%", suspect.heapPercentage())).append(")\n");
                result.append("  Instance Count: ").append(String.format("%,d", suspect.instanceCount())).append("\n");
                if (suspect.objectId() != null) {
                    result.append("  Object ID: ").append(suspect.objectId()).append("\n");
                }
                result.append("\n");
            }

            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to get leak suspects: message={}", e.getMessage(), e);
            return "Error: Failed to get leak suspects: " + e.getMessage();
        }
    }

    @Tool(description = "Get string analysis results showing duplicate strings, longest strings, " +
            "and potential memory waste from string duplication. " +
            "Note: This analysis may need to be run first if results don't exist yet.")
    public String getStringAnalysis() {
        try {
            StringAnalysisReport report = delegate.getStringAnalysis();
            if (report == null) {
                return "String analysis has not been run yet. The user needs to run it from the UI first.";
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
                    if (count >= 20 || result.length() > MAX_RESULT_LENGTH) break;
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
                return "Collection analysis has not been run yet. The user needs to run it from the UI first.";
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
            @ToolParam(description = "Fully qualified class name (e.g., 'java.lang.String', 'java.util.HashMap')")
            String className,
            @ToolParam(description = "Maximum number of instances to return (default: 20, max: 50)")
            Integer limit,
            @ToolParam(description = "Offset for pagination (default: 0)")
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
            @ToolParam(description = "The object ID of the instance to inspect")
            long objectId) {
        try {
            InstanceDetail detail = delegate.getInstanceDetail(objectId, false);
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
            @ToolParam(description = "Maximum number of root entries to return (default: 20, max: 50)")
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
            @ToolParam(description = "Object ID of the parent node in the dominator tree")
            long objectId,
            @ToolParam(description = "Maximum number of children to return (default: 20, max: 50)")
            Integer limit) {
        try {
            int effectiveLimit = limit != null ? Math.min(Math.max(1, limit), 50) : 20;

            DominatorTreeResponse response = delegate.getDominatorTreeChildren(objectId, effectiveLimit);

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
            @ToolParam(description = "Object ID of the target object")
            long objectId,
            @ToolParam(description = "Maximum number of paths to return (default: 3, max: 5)")
            Integer maxPaths) {
        try {
            int effectiveMaxPaths = maxPaths != null ? Math.min(Math.max(1, maxPaths), 5) : 3;

            List<GCRootPath> paths = delegate.getPathsToGCRoot(objectId, true, effectiveMaxPaths);

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
            @ToolParam(description = "Object ID to find referrers for")
            long objectId,
            @ToolParam(description = "Maximum number of referrers to return (default: 20, max: 50)")
            Integer limit) {
        try {
            int effectiveLimit = limit != null ? Math.min(Math.max(1, limit), 50) : 20;

            InstanceTreeResponse response = delegate.getReferrers(objectId, effectiveLimit, 0);

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

    @Tool(description = "Execute an OQL (Object Query Language) query on the heap dump. " +
            "OQL is a SQL-like language for querying heap objects. " +
            "Example: SELECT s FROM java.lang.String s WHERE s.count > 100")
    public String executeOQL(
            @ToolParam(description = "OQL query to execute. Must be a SELECT statement.")
            String query,
            @ToolParam(description = "Maximum number of results to return (default: 50, max: 200)")
            Integer limit) {
        try {
            if (query == null || query.isBlank()) {
                return "Error: OQL query is required";
            }

            int effectiveLimit = limit != null ? Math.min(Math.max(1, limit), 200) : 50;

            OQLQueryResult result = delegate.executeQuery(
                    new OQLQueryRequest(query, effectiveLimit, 0, false));

            StringBuilder sb = new StringBuilder();
            sb.append("OQL Query Result:\n");
            sb.append("Query: ").append(query).append("\n\n");

            if (result.results() != null && !result.results().isEmpty()) {
                for (var row : result.results()) {
                    if (sb.length() > MAX_RESULT_LENGTH) {
                        sb.append("\n... (output truncated)");
                        break;
                    }
                    sb.append(row).append("\n");
                }
                sb.append("\n").append(result.results().size()).append(" result(s) returned");
            } else {
                sb.append("No results found.");
            }

            if (result.errorMessage() != null) {
                sb.append("\nError: ").append(result.errorMessage());
            }

            return sb.toString();
        } catch (Exception e) {
            LOG.error("Failed to execute OQL: query={} message={}", query, e.getMessage(), e);
            return "Error: Failed to execute OQL query: " + e.getMessage();
        }
    }

    private String truncate(String s, int maxLength) {
        if (s == null) return "";
        if (s.length() <= maxLength) return s;
        return s.substring(0, maxLength - 3) + "...";
    }
}
