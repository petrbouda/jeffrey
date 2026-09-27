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
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.PathStep;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.shared.common.BytesUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.FOLLOW_UP;
import static cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.UI_LINK;

/**
 * The answers of the tools that walk single objects: a class's instances, one instance, the dominator
 * tree, the paths to a GC root and an object's referrers. Every object id in them is a decimal string,
 * the form each of these tools takes back.
 */
public final class HeapObjectAnswers {

    static final String OBJECT_ID = "A decimal string, as every heap tool takes it";
    static final String HAS_MORE = "Whether rows remain after these";
    static final String NEXT_CURSOR = "Pass as cursor, with the same other inputs, to read the next page; null on the last";
    static final String UI_LINK_NOTE = "What the linked page shows instead of this exact answer; null when it shows it";

    private static final String PATH_STEP = "%s-> %s%s (ID: %s)%n";
    private static final String FIELD_SEPARATOR = ".";
    private static final String GAP = "%s   ... %d hop(s) not shown ...%n";

    private HeapObjectAnswers() {
    }

    /** Whether a path from a GC root to the object was found. */
    public enum PathStatus {
        OK,
        /** No path was found, and the object is itself a GC root: it is kept alive directly. */
        IS_GC_ROOT,
        /** No path from a GC root was found within the search's reach, weak references not followed. */
        NO_PATH
    }

    public record ClassInstances(
            String profileId,
            String className,
            int totalInstances,
            @McpDescription("The instances on this page, in the engine's order")
            List<Instance> instances,
            @McpDescription(HAS_MORE)
            boolean hasMore,
            @McpNullable
            @McpDescription(NEXT_CURSOR)
            String nextCursor,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink,
            @McpNullable
            @McpDescription(UI_LINK_NOTE)
            String uiLinkNote) {
    }

    public record Instance(
            @McpDescription(OBJECT_ID)
            String objectId,
            long shallowBytes,
            @McpNullable
            @McpDescription("Bytes the object retains; null when not computed")
            Long retainedBytes,
            @McpDescription("The object's identifying fields, each value cut to " + HeapLimits.PARAMETER_CHARS
                    + " characters")
            Map<String, String> details) {
    }

    public record InstanceDetail(
            String profileId,
            @McpDescription(OBJECT_ID)
            String objectId,
            ObjectDetail instance,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink,
            @McpNullable
            @McpDescription(UI_LINK_NOTE)
            String uiLinkNote) {
    }

    public record ObjectDetail(
            String className,
            long shallowBytes,
            @McpNullable
            Long retainedBytes,
            @McpNullable
            @McpDescription("The object as the UI displays it, cut to " + HeapLimits.VALUE_CHARS + " characters")
            String displayValue,
            @McpDescription("The instance fields in declaration order, at most " + HeapLimits.INSTANCE_FIELDS)
            List<Field> fields,
            int omittedFields) {
    }

    public record Field(
            String name,
            @McpNullable
            String type,
            @McpNullable
            @McpDescription("The value as text, cut to " + HeapLimits.VALUE_CHARS + " characters")
            String value,
            @McpNullable
            @McpDescription("The object a reference field points to, a decimal string; null for a primitive or null reference")
            String referencedObjectId,
            @McpNullable
            String referencedClassName) {
    }

    public record DominatorRoots(
            String profileId,
            long totalHeapBytes,
            @McpDescription("The objects retaining the most, largest first")
            List<DominatorEntry> roots,
            @McpDescription("The most roots asked for")
            int limit,
            @McpDescription("Whether more roots exist beyond the ones listed")
            boolean truncated,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    public record DominatorChildren(
            String profileId,
            @McpDescription(OBJECT_ID)
            String parentObjectId,
            @McpDescription("The objects the parent retains, largest first")
            List<DominatorEntry> children,
            @McpDescription(HAS_MORE)
            boolean hasMore,
            @McpNullable
            @McpDescription(NEXT_CURSOR)
            String nextCursor,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink,
            @McpNullable
            @McpDescription(UI_LINK_NOTE)
            String uiLinkNote) {
    }

    public record DominatorEntry(
            @McpDescription(OBJECT_ID)
            String objectId,
            String className,
            @McpNullable
            String fieldName,
            long shallowBytes,
            long retainedBytes,
            @McpDescription("Share of the heap retained, 0-100")
            double retainedPercent,
            @McpDescription("Whether it retains objects of its own; heap_getDominatorTreeChildren lists them")
            boolean hasChildren,
            @McpNullable
            String gcRootKind) {
    }

    public record GcRootPaths(
            PathStatus status,
            @McpNullable
            @McpDescription("Why there is no path; null when one was found")
            String reason,
            String profileId,
            @McpDescription(OBJECT_ID)
            String objectId,
            @McpNullable
            @McpDescription("The kind of GC root the object itself is; null when it is none")
            String targetRootKind,
            @McpDescription("The shortest paths from a GC root to the object")
            List<RootPath> paths,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    public record RootPath(
            @McpDescription(OBJECT_ID)
            String rootObjectId,
            @McpNullable
            String rootClassName,
            @McpNullable
            String rootType,
            @McpNullable
            String threadName,
            @McpNullable
            String stackFrame,
            @McpDescription("The hops from the root, each naming the field that holds the next object. A path "
                    + "longer than " + 2 * HeapLimits.PATH_END_STEPS + " hops keeps the " + HeapLimits.PATH_END_STEPS
                    + " nearest the root and the " + HeapLimits.PATH_END_STEPS + " nearest its end")
            List<Hop> steps,
            @McpDescription("How many hops between the two kept ends were left out; they sit after the first "
                    + HeapLimits.PATH_END_STEPS)
            int omittedSteps) {

        public static RootPath of(GCRootPath path) {
            List<PathStep> all = path.steps() == null ? List.of() : path.steps();
            int kept = HeapLimits.PATH_END_STEPS;
            List<PathStep> shown = all;
            int omitted = 0;
            if (all.size() > 2 * kept) {
                shown = new ArrayList<>(all.subList(0, kept));
                shown.addAll(all.subList(all.size() - kept, all.size()));
                omitted = all.size() - 2 * kept;
            }
            return new RootPath(HeapObjectIds.format(path.rootObjectId()), path.rootClassName(), path.rootType(),
                    path.threadName(), path.stackFrame(), shown.stream().map(Hop::of).toList(), omitted);
        }
    }

    public record Hop(
            @McpDescription(OBJECT_ID)
            String objectId,
            @McpNullable
            String className,
            @McpNullable
            @McpDescription("The field of this object that holds the next one; null on the last hop")
            String fieldName) {

        static Hop of(PathStep step) {
            return new Hop(HeapObjectIds.format(step.objectId()), step.className(), step.fieldName());
        }
    }

    public record Referrers(
            String profileId,
            @McpDescription(OBJECT_ID)
            String objectId,
            int totalReferrers,
            @McpDescription("The objects holding a reference to it, on this page")
            List<Referrer> referrers,
            @McpDescription(HAS_MORE)
            boolean hasMore,
            @McpNullable
            @McpDescription(NEXT_CURSOR)
            String nextCursor,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink,
            @McpNullable
            @McpDescription(UI_LINK_NOTE)
            String uiLinkNote) {
    }

    public record Referrer(
            @McpDescription(OBJECT_ID)
            String objectId,
            @McpNullable
            String className,
            long shallowBytes,
            @McpNullable
            @McpDescription("The field of the referrer that holds the object; null for an array element")
            String fieldName) {
    }

    /** One line per hop of a GC-root path, each naming the field that holds the next object. */
    static void appendSteps(StringBuilder text, RootPath path, String indent) {
        for (int i = 0; i < path.steps().size(); i++) {
            if (i == HeapLimits.PATH_END_STEPS && path.omittedSteps() > 0) {
                text.append(GAP.formatted(indent, path.omittedSteps()));
            }
            Hop hop = path.steps().get(i);
            String field = hop.fieldName() == null ? "" : FIELD_SEPARATOR + hop.fieldName();
            text.append(PATH_STEP.formatted(indent, hop.className(), field, hop.objectId()));
        }
    }

    public static String text(ClassInstances page) {
        StringBuilder text = new StringBuilder("Instances of ").append(page.className()).append(":\n\n")
                .append("Total Instances: ").append(String.format("%,d", page.totalInstances())).append("\n\n")
                .append(String.format("%-20s %-50s %15s%n", "OBJECT ID", "DETAILS", "SHALLOW SIZE"))
                .append(HeapText.rule(87));
        for (Instance instance : page.instances()) {
            text.append(String.format("%-20s %-50s %15s%n",
                    instance.objectId(),
                    HeapText.cut(instance.details().isEmpty() ? "" : instance.details().toString(), 50),
                    BytesUtils.format(instance.shallowBytes())));
        }
        return text.toString();
    }

    public static String text(InstanceDetail detail) {
        ObjectDetail object = detail.instance();
        StringBuilder text = new StringBuilder("Instance Detail (Object ID: ").append(detail.objectId()).append("):\n\n")
                .append("Class: ").append(object.className()).append("\n")
                .append("Shallow Size: ").append(BytesUtils.format(object.shallowBytes())).append("\n\n");
        if (!object.fields().isEmpty()) {
            text.append("Fields:\n")
                    .append(String.format("%-30s %-30s %s%n", "NAME", "TYPE", "VALUE"))
                    .append(HeapText.rule(90));
            for (Field field : object.fields()) {
                text.append(String.format("%-30s %-30s %s%n",
                        HeapText.cut(field.name(), 30),
                        HeapText.cut(field.type(), 30),
                        HeapText.cut(field.value() != null ? field.value() : "null", 60)));
            }
        }
        if (object.omittedFields() > 0) {
            text.append(object.omittedFields()).append(" more field(s) not listed\n");
        }
        return text.toString();
    }

    public static String text(DominatorRoots roots) {
        StringBuilder text = new StringBuilder("Dominator Tree Roots (top retained size holders):\n\n");
        appendEntries(text, roots.roots());
        if (roots.truncated()) {
            text.append("\nMore roots exist beyond these ").append(roots.roots().size()).append("\n");
        }
        return text.toString();
    }

    public static String text(DominatorChildren children) {
        StringBuilder text = new StringBuilder("Dominator Tree Children of Object ID ")
                .append(children.parentObjectId()).append(":\n\n");
        appendEntries(text, children.children());
        return text.toString();
    }

    public static String text(GcRootPaths answer) {
        if (answer.status() != PathStatus.OK) {
            return HeapReportAnswers.statusText(answer.status(), answer.reason());
        }
        StringBuilder text = new StringBuilder("Paths to GC Root for Object ID ").append(answer.objectId()).append(":\n\n");
        if (answer.targetRootKind() != null) {
            text.append("The object is itself a GC root (").append(answer.targetRootKind()).append(").\n\n");
        }
        for (int i = 0; i < answer.paths().size(); i++) {
            RootPath path = answer.paths().get(i);
            text.append("Path #").append(i + 1).append(":\n")
                    .append("  GC Root Type: ").append(path.rootType()).append("\n");
            if (path.threadName() != null) {
                text.append("  Thread: ").append(path.threadName()).append("\n");
            }
            if (path.stackFrame() != null) {
                text.append("  Stack Frame: ").append(path.stackFrame()).append("\n");
            }
            appendSteps(text, path, "  ");
            text.append("\n");
        }
        return text.toString();
    }

    public static String text(Referrers referrers) {
        StringBuilder text = new StringBuilder("Referrers of Object ID ").append(referrers.objectId()).append(":\n\n")
                .append("Total Referrers: ").append(String.format("%,d", referrers.totalReferrers())).append("\n\n")
                .append(String.format("%-50s %15s %20s %s%n", "CLASS", "SIZE", "OBJECT ID", "FIELD"))
                .append(HeapText.rule(103));
        for (Referrer referrer : referrers.referrers()) {
            text.append(String.format("%-50s %15s %20s %s%n",
                    HeapText.cut(referrer.className(), 50),
                    BytesUtils.format(referrer.shallowBytes()),
                    referrer.objectId(),
                    referrer.fieldName() != null ? referrer.fieldName() : ""));
        }
        return text.toString();
    }

    private static void appendEntries(StringBuilder text, List<DominatorEntry> entries) {
        text.append(String.format("%-50s %15s %15s %20s%n", "CLASS", "SHALLOW SIZE", "RETAINED SIZE", "OBJECT ID"))
                .append(HeapText.rule(103));
        for (DominatorEntry entry : entries) {
            text.append(String.format("%-50s %15s %15s %20s%n",
                    HeapText.cut(entry.className(), 50),
                    BytesUtils.format(entry.shallowBytes()),
                    BytesUtils.format(entry.retainedBytes()),
                    entry.objectId()));
        }
    }
}
