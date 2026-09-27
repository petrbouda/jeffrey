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

import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.profile.heapdump.model.HeapThreadState;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.shared.common.BytesUtils;

import java.time.Instant;
import java.util.List;

/**
 * The answers of the {@code heap_} tools that describe the dump as a whole: its summary, the class
 * histogram, its GC roots and its threads — each a record for the structured content, and the
 * fixed-width text the model reads, rendered from that same record.
 */
public final class HeapOverviewAnswers {

    static final String UI_LINK = "The page in the Microscope UI that shows this, for the user";
    static final String FOLLOW_UP = "The calls that take this answer further";

    private static final String UNKNOWN_TIME = "unknown";
    private static final String NEWLINE = "\n";

    private HeapOverviewAnswers() {
    }

    public record Summary(
            String profileId,
            @McpDescription("Shallow bytes of every live object")
            long totalLiveBytes,
            long totalLiveInstances,
            int classCount,
            int gcRootCount,
            @McpNullable
            @McpDescription("When the dump was taken, as UTC epoch milliseconds; null when the dump does not say")
            Long dumpTakenAtEpochMs,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    /**
     * @param omittedClasses 0 when the ranking is shorter than its cap; null when it reached the cap
     *                       and the heap may hold more classes than it shows
     */
    public record Histogram(
            String profileId,
            HistogramOrder order,
            @McpDescription("The most classes asked for")
            int topN,
            @McpDescription("The classes, largest first by the order")
            List<HistogramClass> classes,
            @McpNullable
            @McpDescription("How many classes the cap left out: 0 when the list is shorter than topN, null when "
                    + "it reached topN and the heap may hold more")
            Integer omittedClasses,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    public record HistogramClass(
            String className,
            long instanceCount,
            @McpDescription("Total shallow bytes of the class's instances")
            long shallowBytes) {
    }

    public record GcRoots(
            String profileId,
            long totalRoots,
            @McpDescription("Each GC root type and how many roots it has, most first")
            List<RootType> rootTypes,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    public record RootType(String rootType, long count) {
    }

    public record Threads(
            String profileId,
            int totalThreads,
            @McpDescription("The threads retaining the most first, at most " + HeapLimits.THREADS
                    + "; threads whose retained size is unknown come last")
            List<HeapThread> threads,
            @McpDescription("How many threads the list left out")
            int omittedThreads,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    public record HeapThread(
            @McpDescription("The thread object's id, a decimal string")
            String objectId,
            @McpNullable
            String name,
            boolean daemon,
            int priority,
            @McpNullable
            @McpDescription("Bytes the thread retains; null before the dominator tree is built")
            Long retainedBytes,
            @McpNullable
            Integer frameCount,
            @McpNullable
            Integer localsCount,
            @McpNullable
            Long localsBytes,
            @McpNullable
            HeapThreadState state) {
    }

    public static String text(Summary summary) {
        return """
                Heap Summary:

                Total Live Bytes:      %s (%,d bytes)
                Total Live Instances:  %,d
                Number of Classes:     %,d
                Number of GC Roots:    %,d
                Heap Dump Time:        %s
                """.formatted(
                BytesUtils.format(summary.totalLiveBytes()),
                summary.totalLiveBytes(),
                summary.totalLiveInstances(),
                summary.classCount(),
                summary.gcRootCount(),
                summary.dumpTakenAtEpochMs() == null
                        ? UNKNOWN_TIME
                        : Instant.ofEpochMilli(summary.dumpTakenAtEpochMs()).toString());
    }

    public static String text(Histogram histogram) {
        StringBuilder text = new StringBuilder()
                .append("Class Histogram (top ").append(histogram.topN()).append(" by ")
                .append(histogram.order()).append("):\n\n")
                .append(String.format("%-60s %15s %15s%n", "CLASS", "INSTANCES", "TOTAL SIZE"))
                .append(HeapText.rule(92));
        for (HistogramClass entry : histogram.classes()) {
            text.append(String.format("%-60s %,15d %15s%n",
                    HeapText.cut(entry.className(), 60), entry.instanceCount(), BytesUtils.format(entry.shallowBytes())));
        }
        text.append(NEWLINE).append(histogram.classes().size()).append(" class(es) returned\n");
        return HeapText.omitted(text, histogram.omittedClasses(), "classes").toString();
    }

    public static String text(GcRoots roots) {
        StringBuilder text = new StringBuilder("GC Root Summary:\n\n")
                .append("Total GC Roots: ").append(String.format("%,d", roots.totalRoots())).append("\n\n")
                .append(String.format("%-40s %15s%n", "ROOT TYPE", "COUNT"))
                .append(HeapText.rule(57));
        for (RootType type : roots.rootTypes()) {
            text.append(String.format("%-40s %,15d%n", type.rootType(), type.count()));
        }
        return text.toString();
    }

    public static String text(Threads threads) {
        StringBuilder text = new StringBuilder("Threads in Heap Dump:\n\n")
                .append(String.format("%-50s %20s %15s%n", "THREAD NAME", "OBJECT ID", "RETAINED"))
                .append(HeapText.rule(87));
        for (HeapThread thread : threads.threads()) {
            text.append(String.format("%-50s %20s %15s%n",
                    HeapText.cut(thread.name(), 50),
                    thread.objectId(),
                    thread.retainedBytes() == null ? HeapText.NONE : BytesUtils.format(thread.retainedBytes())));
        }
        text.append(NEWLINE).append(threads.totalThreads()).append(" thread(s) found");
        if (threads.omittedThreads() > 0) {
            text.append(", ").append(threads.omittedThreads()).append(" not listed");
        }
        return text.toString();
    }
}
