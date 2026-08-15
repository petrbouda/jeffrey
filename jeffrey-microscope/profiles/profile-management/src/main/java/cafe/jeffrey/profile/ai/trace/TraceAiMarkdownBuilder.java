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

package cafe.jeffrey.profile.ai.trace;

import cafe.jeffrey.profile.manager.model.trace.TraceContext;
import cafe.jeffrey.profile.manager.model.trace.TraceContextSlice;
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TracePause;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;

import java.util.List;

/**
 * Renders one trace as Markdown written to be read by a model rather than by a person.
 * <p>
 * The preamble carries most of this class's value. Every number in a trace is a term of art — self
 * time is a merged interval computation, not a subtraction; the critical path rests on an assumption
 * that can be wrong; a GC pause and a lock wait are not the same kind of measurement — and a reader
 * given the figures without the definitions will draw confident wrong conclusions from them. The
 * document therefore states its own semantics and its own caveats before it states any data.
 */
public final class TraceAiMarkdownBuilder {

    private static final String AI_PREAMBLE = """
            # How to read this trace

            This document is a **single distributed-trace snapshot** exported by Jeffrey
            (a JVM performance analyst) for AI-assisted interpretation. It describes one
            request end to end: the spans it was made of, and what the JVM itself was
            doing underneath them.

            What makes this different from an ordinary tracing export is the last part.
            Jeffrey holds the JVM's own runtime events in the same database as the spans,
            so a span that was slow because a garbage collection stopped the world can be
            told apart from one that was slow because its own code was slow. Use that.

            ## Sections

            1. **Header (YAML-ish)** — the trace's identity, duration and span count.
            2. **Span tree** — a nested bullet list, two spaces per level, where a bullet
               nested under another was started by it. Each line reads:

                   - <name> [<KIND>] — <total> (<total%>, self <self>)[ !critical][ !error]

            3. **JVM context** — what the runtime was doing during this trace.
            4. **Where the time went** — the same accounting, ranked.

            ## What the numbers mean — read this before reasoning about them

            **`total`** is the span's wall-clock duration, including everything it called.
            Parents contain their children, so totals down a branch do **not** sum to the
            parent: they nest.

            **`self`** is the time the span was doing its *own* work. It is **not** simply
            `total` minus the sum of its children. It is computed by merging the children's
            time intervals and subtracting the merged result, which matters in two ways:

            - Two children that ran **concurrently** are subtracted **once**, not twice.
              Naive subtraction would make such a parent look like it had negative own work.
            - Only children on the **same thread** are subtracted at all. Work handed to
              another thread ran *beside* the parent rather than instead of it, so the
              parent is not credited with having been idle for it.

            A child recorded as outliving its parent is clipped to the parent's own window
            first, so it can only ever cost the parent the stretch the two actually shared.

            **`!critical`** marks a span on the **critical path** — the chain that actually
            determined how long the trace took. Shortening a span not on this chain does not
            make the trace faster. *Caveat:* the critical path assumes a child blocked its
            parent. That is true by construction on the parent's own thread, but a parent
            that forked work to another thread and never waited for it will still show that
            child as blocking. Treat critical-path attribution across threads as a strong
            hint, not a proof.

            ## JVM context — two different kinds of measurement

            Context events fall into two groups, and **summing across the groups is not
            meaningful**:

            - **Global** (`GC_PAUSE`, `SAFEPOINT`) — the JVM stopped *every* application
              thread. These are recorded on VM threads, not on the trace's own thread, and
              they are reported against the trace's whole window. One pause can explain a
              gap in several spans at once.
            - **Thread-scoped** (`MONITOR_BLOCKED`, `PARKED`, `SOCKET_IO`, `FILE_IO`, and
              similar) — one thread was waiting on one thing. These are reported per span.

            *Caveat:* a thread-scoped event is charged to the innermost span that was open
            on its thread when the event **began**. A wait that straddles a child span's
            start is therefore charged wholly to one of the two, not split between them.

            **`OWN_WORK`** in the ranking is the residual — the part of the trace's window
            that no category accounts for, which is the code actually running. It is
            reported explicitly so that the listed waiting is never mistaken for the whole
            story.

            ## Accounting invariant

            The ranking is measured against the **trace's own window**, not against the sum
            of its spans. Spans nest, so summing them counts the same instant once per level
            of the tree and would total far past the time that actually elapsed. The ranked
            categories plus `OWN_WORK` therefore sum to approximately the trace duration —
            approximately, because global pauses and thread-scoped waits are measured
            independently and can overlap each other.

            """;

    private static final String HEADING_TREE = "## Span tree";
    private static final String HEADING_CONTEXT = "## JVM context";
    private static final String HEADING_RANKING = "## Where the time went";
    private static final String HEADING_ANALYSIS = "## How to analyze this trace";

    private static final String ANALYSIS_INSTRUCTION = """
            Work down the critical path, not down the duration ranking: a span can be the
            longest in the trace and still not be why the trace was slow.

            For each candidate, compare `self` against `total`. A span whose self time is a
            small fraction of its total is an orchestrator — the cost is in what it called,
            so look at its children instead. A span whose self time dominates is where real
            work happened, and is worth explaining.

            Then check that self time against the JVM context before blaming the code. If a
            span's self time is largely a GC pause, a lock wait or I/O, then tuning the
            method recovers only the remainder, and the real lever is elsewhere: allocation
            pressure, contention, or the remote call.

            Say plainly when the evidence does not support a conclusion. A trace is one
            sample of one request; a single slow trace does not establish that anything is
            systematically slow.
            """;

    private static final String BULLET_PREFIX = "- ";
    private static final String INDENT_UNIT = "  ";
    private static final String DASH_SEPARATOR = " — ";
    private static final String CRITICAL_MARKER = " !critical";
    private static final String ERROR_MARKER = " !error";
    private static final String ERROR_STATUS = "ERROR";
    private static final String OWN_WORK_CATEGORY = "OWN_WORK";
    private static final String NO_CONTEXT_NOTE =
            "(no GC pauses, safepoints or blocking events were recorded during this trace)";
    private static final String NO_SPANS_NOTE = "(this trace has no spans)";

    /**
     * How many spans a bundle carries before it is cut short. A trace of a few hundred spans is
     * ordinary; one of several thousand is a pathological instrumentation case, and pasting it into
     * a chat window helps nobody.
     */
    private static final int MAX_SPANS = 400;

    private final TraceDetail detail;
    private final TraceContext context;

    public TraceAiMarkdownBuilder(TraceDetail detail, TraceContext context) {
        this.detail = detail;
        this.context = context;
    }

    public String build() {
        StringBuilder out = new StringBuilder(8192);
        out.append(AI_PREAMBLE);
        renderHeader(out);
        out.append('\n');
        renderAnalysisInstruction(out);
        renderTree(out);
        out.append('\n');
        renderContext(out);
        out.append('\n');
        renderRanking(out);
        return out.toString();
    }

    private void renderHeader(StringBuilder out) {
        TraceRow trace = detail.trace();
        out.append("trace_id: ").append(trace.traceId()).append('\n');
        out.append("operation: ").append(trace.rootName()).append('\n');
        out.append("kind: ").append(trace.rootKind()).append('\n');
        out.append("event_type: ").append(trace.rootEventType()).append('\n');
        out.append("duration: ").append(TraceAiFormat.duration(trace.durationNanos())).append('\n');
        out.append("span_count: ").append(trace.spanCount()).append('\n');
        out.append("error_count: ").append(trace.errorCount()).append('\n');
        out.append("started_at_ms_into_recording: ")
                .append(trace.startMillisFromBeginning()).append('\n');
    }

    private void renderAnalysisInstruction(StringBuilder out) {
        out.append(HEADING_ANALYSIS).append('\n').append('\n');
        out.append(ANALYSIS_INSTRUCTION).append('\n');
    }

    /**
     * The tree in the order the waterfall draws it — depth-first from the root, siblings by start —
     * with indentation carrying the parent relationship, so a reader never has to resolve span ids.
     */
    private void renderTree(StringBuilder out) {
        out.append(HEADING_TREE).append('\n').append('\n');

        List<TraceSpanRow> spans = detail.spans();
        if (spans.isEmpty()) {
            out.append(BULLET_PREFIX).append(NO_SPANS_NOTE).append('\n');
            return;
        }

        long traceNanos = detail.trace().durationNanos();
        int rendered = Math.min(spans.size(), MAX_SPANS);
        for (int i = 0; i < rendered; i++) {
            renderSpan(out, spans.get(i), traceNanos);
        }

        // Stated rather than silent: a bundle that quietly stops reads as a complete trace, and a
        // model will happily conclude that the missing spans do not exist.
        if (spans.size() > rendered) {
            out.append(BULLET_PREFIX)
                    .append("(truncated: ")
                    .append(spans.size() - rendered)
                    .append(" further spans were omitted from this export, out of ")
                    .append(spans.size())
                    .append(" in the trace)")
                    .append('\n');
        }
    }

    private void renderSpan(StringBuilder out, TraceSpanRow span, long traceNanos) {
        out.append(INDENT_UNIT.repeat(span.depth())).append(BULLET_PREFIX);
        out.append(span.name());
        out.append(" [").append(span.kind()).append(']');
        out.append(DASH_SEPARATOR).append(TraceAiFormat.duration(span.durationNanos()));
        out.append(" (").append(TraceAiFormat.percent(span.durationNanos(), traceNanos));
        out.append(", self ").append(TraceAiFormat.duration(span.selfDurationNanos())).append(')');

        if (span.criticalPathNanos() > 0) {
            out.append(CRITICAL_MARKER);
        }
        if (ERROR_STATUS.equals(span.status())) {
            out.append(ERROR_MARKER);
            if (span.errorType() != null) {
                out.append(" (").append(span.errorType()).append(')');
            }
        }
        out.append('\n');
    }

    private void renderContext(StringBuilder out) {
        out.append(HEADING_CONTEXT).append('\n').append('\n');

        List<TracePause> pauses = context.pauses();
        boolean hasWaits = !context.spanWaits().isEmpty();
        if (pauses.isEmpty() && !hasWaits) {
            out.append(NO_CONTEXT_NOTE).append('\n');
            return;
        }

        if (!pauses.isEmpty()) {
            out.append("Global — the JVM stopped every application thread:").append('\n').append('\n');
            for (TracePause pause : pauses) {
                out.append(BULLET_PREFIX)
                        .append(pause.category())
                        .append(" (").append(pause.label()).append(')')
                        .append(DASH_SEPARATOR)
                        .append(TraceAiFormat.duration(pause.durationNanos()))
                        .append('\n');
            }
            out.append('\n');
        }

        if (hasWaits) {
            out.append("Thread-scoped — what each span's own thread was waiting on:")
                    .append('\n').append('\n');
            for (TraceSpanRow span : detail.spans()) {
                List<TraceContextSlice> waits = context.spanWaits().get(span.spanId());
                if (waits == null || waits.isEmpty()) {
                    continue;
                }
                out.append(BULLET_PREFIX).append(span.name()).append(DASH_SEPARATOR);
                for (int i = 0; i < waits.size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    TraceContextSlice wait = waits.get(i);
                    out.append(wait.category())
                            .append(' ')
                            .append(TraceAiFormat.duration(wait.totalNanos()));
                    if (wait.occurrences() > 1) {
                        out.append(" over ").append(TraceAiFormat.count(wait.occurrences(), "event"));
                    }
                }
                out.append('\n');
            }
        }
    }

    private void renderRanking(StringBuilder out) {
        out.append(HEADING_RANKING).append('\n').append('\n');

        List<TraceContextSlice> summary = context.summary();
        if (summary.isEmpty()) {
            out.append(NO_CONTEXT_NOTE).append('\n');
            return;
        }

        long traceNanos = detail.trace().durationNanos();
        for (TraceContextSlice slice : summary) {
            if (slice.totalNanos() <= 0) {
                continue;
            }
            out.append(BULLET_PREFIX)
                    .append(slice.category())
                    .append(DASH_SEPARATOR)
                    .append(TraceAiFormat.duration(slice.totalNanos()))
                    .append(" (").append(TraceAiFormat.percent(slice.totalNanos(), traceNanos)).append(')');
            if (OWN_WORK_CATEGORY.equals(slice.category())) {
                out.append(" — the residual: time no category accounts for, i.e. the code running");
            } else if (slice.occurrences() > 0) {
                out.append(", ").append(TraceAiFormat.count(slice.occurrences(), "event"));
            }
            out.append('\n');
        }
    }
}
