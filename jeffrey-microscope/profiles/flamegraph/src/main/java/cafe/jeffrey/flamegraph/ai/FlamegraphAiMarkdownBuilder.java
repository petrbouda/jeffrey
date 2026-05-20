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

package cafe.jeffrey.flamegraph.ai;

import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.shared.common.BytesUtils;
import cafe.jeffrey.shared.common.DurationUtils;
import cafe.jeffrey.shared.common.model.Type;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * Builds an AI-friendly Markdown export from a {@link Frame} tree.
 * <p>
 * Output is plain CommonMark — preamble, a YAML-ish header, then a
 * markdown nested bullet list that mirrors the flamegraph tree. Each
 * frame appears exactly once; depth is encoded by two-space
 * indentation; the parent is the bullet at one indent level less.
 * Per-node metrics expose total, self, and the rolled-up weight of
 * any children pruned below the configured threshold, plus a
 * compact tag describing the frame's JVM tier (C1 / C2 / interpreted
 * / inlined / native / cpp / kernel) so an LLM can spot tuning
 * signals without asking.
 * <p>
 * The walk uses the same math as {@code FlameGraphProtoBuilder}:
 * descend into a child iff
 * {@code child.totalSamples() >= (root.totalSamples * threshold / 100)}.
 */
public final class FlamegraphAiMarkdownBuilder {

    private static final String AI_PREAMBLE = """
            # How to read this profile

            This document is a **single flamegraph snapshot** exported by Jeffrey
            (a JFR analysis tool) for AI-assisted interpretation. Treat it as
            the authoritative description of one event type over one time
            window — not as a generic stack-trace dump.

            ## Sections

            1. **Header (YAML-ish)** — machine context: event type, unit,
               totals, filters in effect, and the prune threshold.
            2. **Call tree** — a markdown nested bullet list where every kept
               frame appears exactly once. Indentation (two spaces per level)
               encodes call depth: a bullet nested under another bullet was
               called by the bullet above it. Each line has the form:

                   - <method> [<type-tag>] — <totalSamples> (<total%>, self <selfSamples>[, +pruned <prunedTailSamples>])

               - `<totalSamples>` / `<total%>` is this frame's full subtree
                 weight — same as the bar width in the visual flamegraph.
               - `<selfSamples>` is what stayed at this exact frame (didn't
                 go deeper). For a true tree leaf, `self ≈ total`; for an
                 interior orchestration frame, `self << total`.
               - `+pruned <N>` (when present) is the cumulative weight of
                 this frame's children that fell below the prune threshold
                 and were dropped from the output. It's annotated here so
                 the deeper detail isn't silently lost — you know "there's
                 more under this frame but it's small".
               - Children are listed sorted by `total` descending, so the
                 heaviest call path under any parent is the first nested
                 bullet.

               **Sample-conservation invariant:** for every kept frame N,
               `Σ surviving-children-totals + selfSamples + prunedTail ==
               N.totalSamples`. Nothing is double-counted; nothing is
               silently dropped.

            ## Frame type tag

            Every bullet (except the synthetic `[root]` container) carries a
            `[...]` tag describing where the frame ran:

            - `[C2]` — JIT-compiled at the C2 tier (fully optimised).
            - `[C1]` — JIT-compiled at the C1 tier (intermediate
              optimisation).
            - `[INT]` — interpreted (no JIT yet).
            - `[INL]` — inlined into the caller (no separate frame at
              runtime).
            - `[C1: 950, C2: 50]` (or any subset of `INT, C1, C2, INL`)
              — samples for this frame are split across compilation
              tiers. This is the key signal for JVM tuning: a hot
              method showing `[C1: ..., C2: ...]` is being promoted;
              one stuck at `[C1]` only is a candidate for investigation
              (CompileThreshold, inlining caps, OSR behaviour, method
              size). Tier order in mixed tags is always INT, C1, C2,
              INL.
            - `[NATIVE]` — JNI / libc / userland native code.
            - `[CPP]` — JVM runtime internals (GC threads, JIT compiler
              threads, safepoint machinery — C++ inside the JVM).
            - `[KERNEL]` — kernel-space samples (syscalls).
            - `[UNKNOWN]` — frame type couldn't be classified.
            - `[SYNTHETIC]` — structural markers (thread names, lambda
              pseudo-frames, allocated/blocking-object placeholders); do
              not treat these as real call frames.

            ## Unit semantics

            - Counts in the tree are always **sample counts**. Each
              sample corresponds to one event.
            - For CPU/wall-clock events: one sample ≈ one sampling interval
              of that thread on-CPU (or on/off-CPU for wall-clock).
            - For allocation events (`OBJECT_ALLOCATION_*`): one sample =
              one recorded allocation event. The header reports the total
              **weight** (bytes allocated) so you can reason about volume
              vs. call-site frequency.
            - For blocking events (`JAVA_MONITOR_*`, `THREAD_PARK`,
              `THREAD_SLEEP`): one sample = one wait. Total **weight** =
              cumulative wait time in nanoseconds.

            ## What was pruned

            Subtrees whose total samples fall below the configured threshold
            (see header `prune_threshold_pct`) are dropped from the tree.
            The weight of any pruned child is rolled up into its parent's
            `+pruned` annotation, so the sample count is preserved even
            when the deeper detail isn't. **Absence is not zero** — if a
            path you expected to see is missing entirely, it was below the
            threshold for its parent.

            ## Format notes

            - Frame labels are method signatures. The sanitiser replaces
              semicolons and newlines inside names with `_` so each bullet
              occupies exactly one line.
            - The list is plain CommonMark — every line is `- <stuff>` at
              some indentation. Render it as a tree.

            ## What you can do with this

            - Identify the dominant on-CPU / allocation / blocking call
              paths by scanning the heaviest top-level bullets and walking
              deeper.
            - Distinguish leaf hotspots (`self ≈ total`) from orchestration
              nodes (`self << total`) at a glance.
            - Spot JVM tuning opportunities via the type tag: methods stuck
              in `[C1]` or split `[C1: ..., C2: ...]` are candidates for
              C2 promotion investigation.
            - Flag pruning-sensitive conclusions: a parent with a large
              `+pruned` annotation has detail you're not seeing.
            - Suggest concrete code investigations or fixes for the worst
              offenders, citing the call path (read by walking from the
              bullet back to its less-indented ancestors).

            ---
            """;

    private static final String ANALYSIS_HEADING = "## How to analyze this profile";
    private static final String TREE_HEADING = "## Call tree";
    private static final String ROOT_LABEL = "[root]";
    private static final String EMPTY_TREE_NOTE = "(empty tree — no samples above the prune threshold)";
    private static final String INDENT_UNIT = "  ";
    private static final String BULLET_PREFIX = "- ";
    private static final String DASH_SEPARATOR = " — ";

    private static final String TAG_C2 = "C2";
    private static final String TAG_C1 = "C1";
    private static final String TAG_INT = "INT";
    private static final String TAG_INL = "INL";
    private static final String TAG_NATIVE = "NATIVE";
    private static final String TAG_CPP = "CPP";
    private static final String TAG_KERNEL = "KERNEL";
    private static final String TAG_UNKNOWN = "UNKNOWN";
    private static final String TAG_SYNTHETIC = "SYNTHETIC";

    private static final String THREADS_MODE_HEADER = "threads_mode";
    private static final String THREADS_MODE_PER_THREAD = "per-thread";
    private static final String THREADS_MODE_AGGREGATED = "aggregated";

    private static final LongFunction<String> ALLOCATION_FORMATTER =
            weight -> BytesUtils.format(weight) + " Allocated";
    private static final LongFunction<String> BLOCKING_FORMATTER =
            weight -> DurationUtils.formatNanos2Units(weight) + " Blocked";
    private static final LongFunction<String> LATENCY_FORMATTER =
            weight -> DurationUtils.formatNanos2Units(weight) + " Latency";

    private final Type eventType;
    private final AiExportConfig config;
    private final ExportContext ctx;
    private final AnalysisCategory category;
    private final List<HeaderField> extraHeaderFields = new ArrayList<>();

    public FlamegraphAiMarkdownBuilder(Type eventType, AiExportConfig config) {
        this.eventType = eventType;
        this.config = config;
        this.ctx = resolveContext(eventType);
        this.category = AnalysisCategory.resolve(eventType);
    }

    /**
     * Adds an extra {@code key: value} line to the YAML-ish header.
     * Useful for filter/time-range disclosure provided by the caller.
     */
    public FlamegraphAiMarkdownBuilder withHeaderField(String key, String value) {
        extraHeaderFields.add(new HeaderField(key, value));
        return this;
    }

    /**
     * Declares whether the flamegraph was produced in per-thread or
     * aggregated mode. Emitted as {@code threads_mode: per-thread} or
     * {@code threads_mode: aggregated} in the YAML-ish header.
     */
    public FlamegraphAiMarkdownBuilder withThreadMode(boolean perThread) {
        extraHeaderFields.add(new HeaderField(
                THREADS_MODE_HEADER,
                perThread ? THREADS_MODE_PER_THREAD : THREADS_MODE_AGGREGATED));
        return this;
    }

    public String build(Frame root) {
        long totalSamples = root.totalSamples();
        long totalWeight = root.totalWeight();
        long minSamples = (long) (totalSamples * config.minFrameThresholdPct() / 100.0);

        StringBuilder out = new StringBuilder(8192);
        out.append(AI_PREAMBLE).append('\n');
        renderHeader(out, totalSamples, totalWeight);
        out.append('\n').append('\n');
        renderAnalysisInstruction(out);
        renderTree(out, root, totalSamples, minSamples);
        return out.toString();
    }

    private void renderAnalysisInstruction(StringBuilder out) {
        out.append(ANALYSIS_HEADING).append('\n').append('\n');
        out.append(category.instruction());
        out.append('\n').append('\n');
    }

    private void renderHeader(StringBuilder out, long totalSamples, long totalWeight) {
        out.append("event_type: ").append(eventType.code()).append('\n');
        out.append("unit: ").append(ctx.unit).append('\n');
        out.append("samples_total: ").append(totalSamples).append('\n');
        if (ctx.weightUnit != null) {
            out.append("weight_unit: ").append(ctx.weightUnit).append('\n');
            out.append("weight_total: ").append(totalWeight);
            if (ctx.weightFormatter != null) {
                out.append(" (").append(ctx.weightFormatter.apply(totalWeight)).append(')');
            }
            out.append('\n');
        }
        for (HeaderField field : extraHeaderFields) {
            out.append(field.key()).append(": ").append(field.value()).append('\n');
        }
        out.append("prune_threshold_pct: ").append(config.minFrameThresholdPct());
    }

    private void renderTree(StringBuilder out, Frame root, long totalSamples, long minSamples) {
        out.append(TREE_HEADING).append('\n').append('\n');
        renderRootLine(out, root, totalSamples, minSamples);

        List<Map.Entry<String, Frame>> survivingChildren = survivingChildrenSorted(root, minSamples);
        if (survivingChildren.isEmpty()) {
            out.append(INDENT_UNIT).append(BULLET_PREFIX).append(EMPTY_TREE_NOTE).append('\n');
            return;
        }

        for (Map.Entry<String, Frame> entry : survivingChildren) {
            renderFrame(out, entry.getKey(), entry.getValue(), 1, totalSamples, minSamples);
        }
    }

    private void renderRootLine(StringBuilder out, Frame root, long totalSamples, long minSamples) {
        out.append(BULLET_PREFIX).append(ROOT_LABEL).append(DASH_SEPARATOR).append(totalSamples);
        if (totalSamples > 0) {
            out.append(" (100%");
            long prunedTail = prunedTailSamples(root, minSamples);
            if (prunedTail > 0) {
                out.append(", +pruned ").append(prunedTail);
            }
            out.append(')');
        }
        out.append('\n');
    }

    private void renderFrame(
            StringBuilder out,
            String name,
            Frame frame,
            int depth,
            long totalSamples,
            long minSamples) {

        out.append(INDENT_UNIT.repeat(depth)).append(BULLET_PREFIX);
        out.append(sanitizeFrame(name));
        out.append(" [").append(resolveTypeTag(frame)).append(']');
        out.append(DASH_SEPARATOR).append(frame.totalSamples());
        out.append(" (").append(formatPercent(frame.totalSamples(), totalSamples));
        out.append(", self ").append(frame.selfSamples());
        long prunedTail = prunedTailSamples(frame, minSamples);
        if (prunedTail > 0) {
            out.append(", +pruned ").append(prunedTail);
        }
        out.append(')').append('\n');

        for (Map.Entry<String, Frame> entry : survivingChildrenSorted(frame, minSamples)) {
            renderFrame(out, entry.getKey(), entry.getValue(), depth + 1, totalSamples, minSamples);
        }
    }

    private static List<Map.Entry<String, Frame>> survivingChildrenSorted(Frame frame, long minSamples) {
        List<Map.Entry<String, Frame>> survivors = new ArrayList<>();
        for (Map.Entry<String, Frame> entry : frame.entrySet()) {
            if (entry.getValue().totalSamples() >= minSamples) {
                survivors.add(entry);
            }
        }
        survivors.sort(Comparator.<Map.Entry<String, Frame>>comparingLong(e -> e.getValue().totalSamples()).reversed());
        return survivors;
    }

    private static long prunedTailSamples(Frame frame, long minSamples) {
        long pruned = 0;
        for (Frame child : frame.values()) {
            if (child.totalSamples() < minSamples) {
                pruned += child.totalSamples();
            }
        }
        return pruned;
    }

    private static String formatPercent(long part, long whole) {
        if (whole <= 0) {
            return "0.0%";
        }
        double pct = 100.0 * part / whole;
        return String.format(Locale.ROOT, "%.1f%%", pct);
    }

    private static String resolveTypeTag(Frame frame) {
        long intS = frame.interpretedSamples();
        long c1S = frame.c1Samples();
        long c2S = frame.jitCompiledSamples();
        long inlS = frame.inlinedSamples();

        int nonZeroTiers = (intS > 0 ? 1 : 0)
                + (c1S > 0 ? 1 : 0)
                + (c2S > 0 ? 1 : 0)
                + (inlS > 0 ? 1 : 0);

        if (nonZeroTiers == 0) {
            return nonJavaTag(frame.frameType());
        }

        if (nonZeroTiers == 1) {
            if (c2S > 0) {
                return TAG_C2;
            }
            if (c1S > 0) {
                return TAG_C1;
            }
            if (intS > 0) {
                return TAG_INT;
            }
            return TAG_INL;
        }

        StringBuilder tag = new StringBuilder();
        if (intS > 0) {
            appendTierEntry(tag, TAG_INT, intS);
        }
        if (c1S > 0) {
            appendTierEntry(tag, TAG_C1, c1S);
        }
        if (c2S > 0) {
            appendTierEntry(tag, TAG_C2, c2S);
        }
        if (inlS > 0) {
            appendTierEntry(tag, TAG_INL, inlS);
        }
        return tag.toString();
    }

    private static String nonJavaTag(FrameType frameType) {
        return switch (frameType) {
            case NATIVE -> TAG_NATIVE;
            case CPP -> TAG_CPP;
            case KERNEL -> TAG_KERNEL;
            case UNKNOWN -> TAG_UNKNOWN;
            case THREAD_NAME_SYNTHETIC,
                 ALLOCATED_OBJECT_SYNTHETIC,
                 ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC,
                 ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC,
                 BLOCKING_OBJECT_SYNTHETIC,
                 LAMBDA_SYNTHETIC,
                 COLLAPSED_SYNTHETIC,
                 HIGHLIGHTED_WARNING -> TAG_SYNTHETIC;
            case C1_COMPILED -> TAG_C1;
            case JIT_COMPILED -> TAG_C2;
            case INTERPRETED -> TAG_INT;
            case INLINED -> TAG_INL;
        };
    }

    private static void appendTierEntry(StringBuilder b, String label, long count) {
        if (b.length() > 0) {
            b.append(", ");
        }
        b.append(label).append(": ").append(count);
    }

    private static String sanitizeFrame(String title) {
        if (title == null) {
            return "?";
        }
        return title.replace(';', '_').replace('\n', '_').replace('\r', '_');
    }

    private static ExportContext resolveContext(Type eventType) {
        if (eventType.isAllocationEvent()) {
            return new ExportContext("samples", "bytes", ALLOCATION_FORMATTER);
        }
        if (eventType.isBlockingEvent()) {
            return new ExportContext("samples", "nanoseconds", BLOCKING_FORMATTER);
        }
        if (eventType.isMethodTraceEvent()) {
            return new ExportContext("samples", "nanoseconds", LATENCY_FORMATTER);
        }
        return new ExportContext("samples", null, null);
    }

    private record ExportContext(String unit, String weightUnit, LongFunction<String> weightFormatter) {
    }

    private record HeaderField(String key, String value) {
    }

}
