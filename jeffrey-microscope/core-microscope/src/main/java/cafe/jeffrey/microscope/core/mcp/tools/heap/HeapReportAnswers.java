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


package cafe.jeffrey.microscope.core.mcp.tools.heap;

import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectAnswers.RootPath;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.profile.heapdump.model.HintKind;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.shared.common.BytesUtils;

import java.util.List;

import static cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.FOLLOW_UP;
import static cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.UI_LINK;

/**
 * The answers of the report tools — biggest objects, leak suspects, class-loader leak chains, top
 * consumers, strings and collections — each read from a report {@code heap_prepare} computes once.
 * Every one answers {@link ReportStatus#NOT_RUN_YET} with empty data until that report exists.
 */
public final class HeapReportAnswers {

    static final String STATUS = "Whether the report was computed; NOT_RUN_YET until heap_prepare computes it";
    static final String REASON = "Why there is no data, and what computes it; null when the report is read";

    private static final String STATUS_LINE = "status: %s\n%s\n";

    private HeapReportAnswers() {
    }

    public record BiggestObjects(
            @McpDescription(STATUS)
            ReportStatus status,
            @McpNullable
            @McpDescription(REASON)
            String reason,
            String profileId,
            @McpNullable
            Long totalHeapBytes,
            @McpNullable
            @McpDescription("Bytes the objects listed retain together")
            Long totalRetainedBytes,
            @McpDescription("The objects retaining the most, largest first")
            List<BiggestObject> objects,
            @McpNullable
            @McpDescription("How many objects the cap left out: 0 when the list is shorter than topN, null when "
                    + "it reached topN")
            Integer omittedObjects,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    public record BiggestObject(
            @McpDescription("A decimal string, as every heap tool takes it")
            String objectId,
            String className,
            long shallowBytes,
            long retainedBytes) {
    }

    public record LeakSuspects(
            @McpDescription(STATUS)
            ReportStatus status,
            @McpNullable
            @McpDescription(REASON)
            String reason,
            String profileId,
            @McpNullable
            Long totalHeapBytes,
            @McpNullable
            @McpDescription("Bytes the suspects retain together")
            Long analyzedBytes,
            @McpDescription("The suspects, most suspicious first")
            List<Suspect> suspects,
            @McpDescription("The class loaders the suspects belong to, retaining the most first")
            List<LoaderLeak> leakingClassLoaders,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    public record Suspect(
            int rank,
            String className,
            @McpNullable
            @McpDescription("The object at the root of the retained cluster, a decimal string; null when the "
                    + "suspect is a class rather than one object")
            String objectId,
            long retainedBytes,
            @McpDescription("Share of the heap retained, 0-100")
            double heapPercent,
            int instanceCount,
            @McpNullable
            String reason,
            @McpNullable
            @McpDescription("Where the retained objects accumulate; null when the heuristic found none")
            String accumulationPoint,
            @McpNullable
            String accumulationPointObjectId,
            @McpNullable
            String accumulationPointClass,
            double leakScore,
            @McpDescription("The class loader's object id, a decimal string; 0 is the bootstrap loader")
            String classLoaderId,
            @McpNullable
            String classLoaderClassName,
            @McpDescription("The classes contributing most to the cluster, at most " + HeapLimits.SUSPECT_CONTRIBUTORS)
            List<Contributor> topContributors,
            @McpDescription("How many contributing classes the list left out")
            int omittedContributors) {
    }

    public record Contributor(
            String className,
            int instanceCount,
            long retainedBytes,
            @McpDescription("Share of the suspect's cluster, 0-100")
            double percentOfCluster) {
    }

    public record LoaderLeak(
            @McpDescription("The class loader's object id, a decimal string")
            String classLoaderId,
            @McpNullable
            String classLoaderClassName,
            long retainedBytes,
            int suspectCount) {
    }

    public record ClassLoaderChains(
            @McpDescription(STATUS)
            ReportStatus status,
            @McpNullable
            @McpDescription(REASON)
            String reason,
            String profileId,
            @McpDescription("The suspicious class loaders retaining the most first, at most "
                    + HeapLimits.LOADER_CHAINS + "; empty when none was found suspicious")
            List<LoaderChain> chains,
            @McpNullable
            @McpDescription("How many suspicious class loaders the list left out; null when the report's own list is "
                    + "full, since the report traces only the " + HeapLimits.ENGINE_LOADER_CHAINS
                    + " retaining the most, and on NOT_RUN_YET")
            Integer omittedChains,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    public record LoaderChain(
            @McpDescription("The class loader's object id, a decimal string")
            String classLoaderId,
            @McpNullable
            String classLoaderClassName,
            int classCount,
            long retainedBytes,
            @McpDescription("Whether a class it loaded is also loaded by another loader")
            boolean duplicateClasses,
            @McpDescription("Matched leak patterns, such as a ThreadLocal or a registered JDBC driver")
            List<Hint> causeHints,
            @McpNullable
            @McpDescription("What keeps the loader alive; null when no path was computed")
            RootPath gcRootPath) {
    }

    public record Hint(
            HintKind kind,
            @McpNullable
            String description,
            @McpDescription("The object the pattern was found on, a decimal string")
            String objectId) {
    }

    public record TopConsumers(
            @McpDescription(STATUS)
            ReportStatus status,
            @McpNullable
            @McpDescription(REASON)
            String reason,
            String profileId,
            @McpNullable
            Long totalHeapBytes,
            @McpDescription("Memory grouped by package and class loader, the largest shallow size first, at most "
                    + HeapLimits.CONSUMERS)
            List<Consumer> consumers,
            @McpNullable
            @McpDescription("How many consumers the list left out; null when the report's own list is full, since "
                    + "the report keeps only the " + HeapLimits.ENGINE_CONSUMERS + " largest, and on NOT_RUN_YET")
            Integer omittedConsumers,
            @McpNullable
            @McpDescription("Memory grouped by package, the largest shallow size first, at most " + HeapLimits.CONSUMERS
                    + "; null when the report did not compute the per-package component report, as the current "
                    + "engine does not")
            List<Component> components,
            @McpNullable
            @McpDescription("How many components the list left out; null when there is no component report")
            Integer omittedComponents,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    public record Consumer(
            @McpNullable
            String packageName,
            @McpDescription("The class loader's object id, a decimal string")
            String classLoaderId,
            @McpNullable
            String classLoaderClassName,
            @McpDescription("Shallow bytes of the group's instances; the report computes no retained size")
            long shallowBytes,
            int classCount,
            long instanceCount) {
    }

    public record Component(
            @McpNullable
            String packageName,
            long shallowBytes,
            int classCount,
            long instanceCount) {
    }

    public record StringAnalysis(
            @McpDescription(STATUS)
            ReportStatus status,
            @McpNullable
            @McpDescription(REASON)
            String reason,
            String profileId,
            @McpNullable
            StringTotals totals,
            @McpDescription("Duplicated strings, the biggest saving first, at most " + HeapLimits.STRING_OPPORTUNITIES)
            List<DedupOpportunity> opportunities,
            @McpNullable
            @McpDescription("How many opportunities the list left out; null when the report's own list reached "
                    + "the cap it was built with, or the report does not record that cap, since it may have cut "
                    + "more itself; null on NOT_RUN_YET")
            Integer omittedOpportunities,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    public record StringTotals(
            long totalStrings,
            long totalShallowBytes,
            long uniqueArrays,
            long sharedArrays,
            @McpDescription("Bytes already saved by strings sharing their backing array")
            long savedByDedupBytes,
            @McpDescription("Bytes deduplicating the rest would save")
            long potentialSavingsBytes) {
    }

    public record DedupOpportunity(
            @McpNullable
            @McpDescription("A preview of the string's content, up to " + HeapLimits.STRING_CONTENT_CHARS
                    + " characters; the report ends a preview it cut with an ellipsis")
            String content,
            @McpDescription("Whether the preview is cut from a longer string")
            boolean contentTruncated,
            @McpDescription("How many copies the heap holds")
            int count,
            long arrayBytes,
            long savingsBytes) {
    }

    public record CollectionAnalysis(
            @McpDescription(STATUS)
            ReportStatus status,
            @McpNullable
            @McpDescription(REASON)
            String reason,
            String profileId,
            @McpNullable
            CollectionTotals totals,
            @McpDescription("Each collection type, as the report orders them")
            List<CollectionType> types,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    public record CollectionTotals(int totalCollections, int emptyCollections, long wastedBytes) {
    }

    public record CollectionType(
            String collectionType,
            int count,
            int emptyCount,
            long wastedBytes,
            @McpDescription("Average share of capacity in use, 0-1")
            double avgFillRatio) {
    }

    /** The first lines of a report's text when it has no data: its status and why. */
    public static String statusText(Enum<?> status, String reason) {
        return STATUS_LINE.formatted(status.name(), reason);
    }

    public static String text(BiggestObjects report) {
        if (report.status() != ReportStatus.OK) {
            return statusText(report.status(), report.reason());
        }
        StringBuilder text = new StringBuilder("Biggest Objects Report:\n\n")
                .append("Total Heap Size: ").append(BytesUtils.format(report.totalHeapBytes())).append("\n")
                .append("Total Retained by Top Objects: ").append(BytesUtils.format(report.totalRetainedBytes()))
                .append("\n\n")
                .append(String.format("%-50s %15s %15s %20s%n", "CLASS", "SHALLOW SIZE", "RETAINED SIZE", "OBJECT ID"))
                .append(HeapText.rule(103));
        for (BiggestObject entry : report.objects()) {
            text.append(String.format("%-50s %15s %15s %20s%n",
                    HeapText.cut(entry.className(), 50),
                    BytesUtils.format(entry.shallowBytes()),
                    BytesUtils.format(entry.retainedBytes()),
                    entry.objectId()));
        }
        return HeapText.omitted(text, report.omittedObjects(), "objects").toString();
    }

    public static String text(LeakSuspects report) {
        if (report.status() != ReportStatus.OK) {
            return statusText(report.status(), report.reason());
        }
        StringBuilder text = new StringBuilder("Leak Suspects Report:\n\n")
                .append("Total Heap Size: ").append(BytesUtils.format(report.totalHeapBytes())).append("\n")
                .append("Analyzed Bytes: ").append(BytesUtils.format(report.analyzedBytes())).append("\n\n");
        if (report.suspects().isEmpty()) {
            return text.append("No leak suspects identified.\n").toString();
        }
        for (Suspect suspect : report.suspects()) {
            text.append("Suspect #").append(suspect.rank()).append(": ").append(suspect.className()).append("\n");
            appendLine(text, "  Reason: ", suspect.reason());
            appendLine(text, "  Accumulation Point: ", suspect.accumulationPoint());
            if (suspect.accumulationPointObjectId() != null) {
                text.append("  Accumulation Point Object ID: ").append(suspect.accumulationPointObjectId())
                        .append(" (").append(suspect.accumulationPointClass()).append(")\n");
            }
            text.append("  Retained Size: ").append(BytesUtils.format(suspect.retainedBytes()))
                    .append(" (").append(String.format("%.1f%%", suspect.heapPercent())).append(")\n")
                    .append("  Leak Score: ").append(String.format("%.1f", suspect.leakScore())).append("\n")
                    .append("  Instance Count: ").append(String.format("%,d", suspect.instanceCount())).append("\n")
                    .append("  Class Loader: ").append(suspect.classLoaderClassName())
                    .append(" (id=").append(suspect.classLoaderId()).append(")\n");
            appendLine(text, "  Cluster Root Object ID: ", suspect.objectId());
            if (!suspect.topContributors().isEmpty()) {
                text.append("  Top Contributing Classes:\n");
                for (Contributor entry : suspect.topContributors()) {
                    text.append("    - ").append(entry.className())
                            .append(": ").append(String.format("%,d", entry.instanceCount())).append(" instances, ")
                            .append(BytesUtils.format(entry.retainedBytes()))
                            .append(" (").append(String.format("%.1f%%", entry.percentOfCluster()))
                            .append(" of cluster)\n");
                }
            }
            if (suspect.omittedContributors() > 0) {
                text.append("    ").append(suspect.omittedContributors()).append(" more contributing class(es) not listed\n");
            }
            text.append("\n");
        }
        if (!report.leakingClassLoaders().isEmpty()) {
            text.append("Top Leaking Class Loaders (across all suspects):\n");
            for (LoaderLeak loader : report.leakingClassLoaders()) {
                text.append("  - ").append(loader.classLoaderClassName())
                        .append(": ").append(BytesUtils.format(loader.retainedBytes()))
                        .append(" across ").append(loader.suspectCount()).append(" suspect(s)\n");
            }
        }
        return text.toString();
    }

    public static String text(ClassLoaderChains report) {
        if (report.status() != ReportStatus.OK) {
            return statusText(report.status(), report.reason());
        }
        if (report.chains().isEmpty()) {
            return "No suspicious class loaders detected.\n";
        }
        StringBuilder text = new StringBuilder("Class Loader Leak Chains:\n\n");
        for (LoaderChain chain : report.chains()) {
            text.append(chain.classLoaderClassName()).append(" (id=").append(chain.classLoaderId()).append(")\n")
                    .append("  Retained: ").append(BytesUtils.format(chain.retainedBytes()))
                    .append(", classes: ").append(chain.classCount()).append("\n");
            if (chain.duplicateClasses()) {
                text.append("  ! At least one class is also loaded by another loader (duplicate)\n");
            }
            if (!chain.causeHints().isEmpty()) {
                text.append("  Cause hints: ");
                for (Hint hint : chain.causeHints()) {
                    text.append(hint.kind()).append("(").append(hint.description()).append(") ");
                }
                text.append("\n");
            }
            if (chain.gcRootPath() != null) {
                RootPath path = chain.gcRootPath();
                text.append("  GC root: ").append(path.rootType()).append(" — ").append(path.rootClassName())
                        .append("\n")
                        .append("  Path (").append(path.steps().size() + path.omittedSteps()).append(" hop(s)):\n");
                HeapObjectAnswers.appendSteps(text, path, "    ");
            } else {
                text.append("  No GC-root path computed\n");
            }
            text.append("\n");
        }
        return HeapText.omitted(text, report.omittedChains(), "suspicious class loaders").toString();
    }

    public static String text(TopConsumers report) {
        if (report.status() != ReportStatus.OK) {
            return statusText(report.status(), report.reason());
        }
        StringBuilder text = new StringBuilder(
                "Top Consumers (by package + class loader, largest shallow size first):\n");
        for (Consumer entry : report.consumers()) {
            text.append("  ").append(entry.packageName())
                    .append(" [").append(entry.classLoaderClassName()).append("]")
                    .append(" — ").append(BytesUtils.format(entry.shallowBytes())).append(" shallow")
                    .append(", ").append(entry.classCount()).append(" classes / ")
                    .append(String.format("%,d", entry.instanceCount())).append(" instances\n");
        }
        HeapText.omitted(text, report.omittedConsumers(), "consumers");
        text.append("\nComponent Report (per-package):");
        if (report.components() == null) {
            return text.append(" not computed\n").toString();
        }
        text.append("\n");
        for (Component entry : report.components()) {
            text.append("  ").append(entry.packageName())
                    .append(" — ").append(BytesUtils.format(entry.shallowBytes())).append(" shallow")
                    .append(", ").append(entry.classCount()).append(" classes\n");
        }
        return HeapText.omitted(text, report.omittedComponents(), "components").toString();
    }

    public static String text(StringAnalysis report) {
        if (report.status() != ReportStatus.OK) {
            return statusText(report.status(), report.reason());
        }
        StringTotals totals = report.totals();
        StringBuilder text = new StringBuilder("String Analysis Report:\n\n")
                .append("Total Strings: ").append(String.format("%,d", totals.totalStrings())).append("\n")
                .append("Total String Shallow Size: ").append(BytesUtils.format(totals.totalShallowBytes())).append("\n")
                .append("Unique Arrays: ").append(String.format("%,d", totals.uniqueArrays())).append("\n")
                .append("Shared Arrays: ").append(String.format("%,d", totals.sharedArrays())).append("\n")
                .append("Memory Saved by Dedup: ").append(BytesUtils.format(totals.savedByDedupBytes())).append("\n")
                .append("Potential Savings: ").append(BytesUtils.format(totals.potentialSavingsBytes())).append("\n\n");
        if (!report.opportunities().isEmpty()) {
            text.append("Top Deduplication Opportunities:\n")
                    .append(String.format("%-50s %10s %15s%n", "CONTENT", "COUNT", "SAVINGS"))
                    .append(HeapText.rule(77));
            for (DedupOpportunity entry : report.opportunities()) {
                text.append(String.format("%-50s %,10d %15s%n",
                        HeapText.cut(entry.content(), 50), entry.count(), BytesUtils.format(entry.savingsBytes())));
            }
            HeapText.omitted(text, report.omittedOpportunities(), "opportunities");
        }
        return text.toString();
    }

    public static String text(CollectionAnalysis report) {
        if (report.status() != ReportStatus.OK) {
            return statusText(report.status(), report.reason());
        }
        CollectionTotals totals = report.totals();
        StringBuilder text = new StringBuilder("Collection Analysis Report:\n\n")
                .append("Total Collections: ").append(String.format("%,d", totals.totalCollections())).append("\n")
                .append("Total Empty: ").append(String.format("%,d", totals.emptyCollections())).append("\n")
                .append("Total Wasted Bytes: ").append(BytesUtils.format(totals.wastedBytes())).append("\n\n");
        for (CollectionType type : report.types()) {
            text.append("Collection Type: ").append(type.collectionType()).append("\n")
                    .append("  Total Count: ").append(String.format("%,d", type.count())).append("\n")
                    .append("  Empty: ").append(String.format("%,d", type.emptyCount())).append("\n")
                    .append("  Wasted Bytes: ").append(BytesUtils.format(type.wastedBytes())).append("\n")
                    .append("  Avg Fill Ratio: ").append(String.format("%.2f", type.avgFillRatio())).append("\n\n");
        }
        return text.toString();
    }

    private static void appendLine(StringBuilder text, String label, String value) {
        if (value != null) {
            text.append(label).append(value).append("\n");
        }
    }
}
