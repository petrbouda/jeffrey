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
            5. **I/O operations** — the socket and file operations grouped by what
               they were against, and the shape they made.
            6. **Exceptions** — every throw recorded inside the trace, grouped.

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

            ## I/O operations — read the shape, not just the total

            Socket and file I/O appears twice in this document on purpose. Each recorded
            operation is promoted into a leaf span, so it is visible in the tree at the place
            it happened; the **I/O operations** section then groups those same operations by
            what they were against — a file path, or a `host:port` — and reports the shape of
            each group:

                - <kind> <target> — <ops> ops · bytes <total>, mean <mean>/op · time <total>, mean <mean>/op, max <max>[ !small-ops]

            The **mean bytes per operation** is the buffering figure, and the reason this
            section exists. Many operations against one target with a small mean is the
            fingerprint of a missing or undersized buffer: an unbuffered `InputStream` or
            `OutputStream`, a read handed a small `byte[]`, a serializer writing a field at a
            time. Java's `BufferedInputStream` buffers 8 KiB by default, so a mean far below
            that across many operations means the buffer is absent or smaller than the work
            needs. Rows like that are marked **`!small-ops`** — at least 8 operations with a
            mean under 4 KiB. Treat the marker as a shape, not a verdict: a protocol whose
            messages genuinely are 200 bytes long trips it too.

            For **sockets** the same shape usually means something else. Not a missing buffer
            but a chatty exchange: a round trip per item where one batched request would do.
            There the mean *time* per operation is the figure to weigh, because each operation
            pays a network latency that no byte count explains — and unlike a small file read,
            a small socket read is often the peer's fault rather than this code's.

            **`File force`** is an fsync. It moves no bytes, so it is reported without them,
            and its cost is the durability barrier itself. Many of them in one trace usually
            means a flush per record where a flush per batch was intended.

            *Caveat, and it is a large one:* JFR records a socket or file operation only when
            its duration exceeded the recording's I/O threshold. Every operation faster than
            that threshold is simply absent. **The op counts and byte totals here are therefore
            lower bounds**, and the mean is biased toward the slow operations that made the
            cut. A trace showing fifteen small reads may have performed fifteen thousand. Read
            these counts as evidence that a pattern exists, never as a measurement of how often
            it does — and never subtract them from a span's self time.

            ## Exceptions — a throw is not an error

            The **Exceptions** section lists the throws the recording captured inside this
            trace, grouped by thrown class and message. Each throw is charged to the innermost
            span that was open on its thread when it was thrown — the same attribution rule the
            thread-scoped context events use.

            Two things to keep straight:

            - **A throw is not a failure.** The JVM records every throw, including ones caught
              a frame later and used as ordinary control flow. Only a throw marked
              **`!escaped`** is why its span's status is `ERROR`; a span can be perfectly `OK`
              and still have thrown hundreds of times.
            - **Volume is itself a cost.** Constructing an exception captures a stack trace,
              which is not free. A group with a high count and nothing escaped is the
              exceptions-as-control-flow pattern: the trace is not failing, it is paying for
              something a return value would have done.

            *Caveat:* these events are disabled in many recording configurations, and some
            settings cap how many are emitted. An empty section means the recording captured no
            throws — not that none were thrown.

            """;

    private static final String HEADING_TREE = "## Span tree";
    private static final String HEADING_CONTEXT = "## JVM context";
    private static final String HEADING_RANKING = "## Where the time went";
    private static final String HEADING_IO = "## I/O operations";
    private static final String HEADING_EXCEPTIONS = "## Exceptions";
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

            When a span's time turns out to be I/O, read the I/O section before concluding
            that the disk or the remote system is slow. Many small operations against one
            target is a shape problem on this side of the call, and a faster disk or a faster
            peer does not fix it — a buffer, a batch or a single round trip does.

            Read the throws the same way. A group that never escaped is a cost, not a failure,
            and worth naming as one; a group that escaped is why something broke, and belongs
            in the answer before any timing does.

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
    private static final String NO_IO_NOTE =
            "(no socket or file operations were recorded during this trace)";
    private static final String NO_THROWS_NOTE =
            "(no exceptions or errors were recorded during this trace)";
    private static final String SMALL_OPS_MARKER = " !small-ops";
    private static final String ESCAPED_MARKER = " !escaped";

    /**
     * How many spans a bundle carries before it is cut short. A trace of a few hundred spans is
     * ordinary; one of several thousand is a pathological instrumentation case, and pasting it into
     * a chat window helps nobody.
     */
    private static final int MAX_SPANS = 400;

    /**
     * How many I/O targets and throw groups a bundle carries. Both are already aggregations, so the
     * tail past this point is long and flat — a hundredth-ranked file path costs a reader more
     * attention than it repays.
     */
    private static final int MAX_IO_TARGETS = 25;
    private static final int MAX_THROW_GROUPS = 25;

    /** How many span names one throw group names before it summarises the rest. */
    private static final int MAX_THROW_SITES = 5;

    /** How much of an exception message survives. Enough to identify it, not enough to be a page. */
    private static final int MAX_MESSAGE_CHARS = 200;

    private final TraceDetail detail;
    private final TraceContext context;
    private final TraceIoSummary io;
    private final TraceThrowSummary throwSummary;

    public TraceAiMarkdownBuilder(TraceDetail detail, TraceContext context) {
        this.detail = detail;
        this.context = context;
        this.io = TraceIoSummary.of(detail.spans());
        this.throwSummary = TraceThrowSummary.of(detail.exceptions(), detail.spans());
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
        out.append('\n');
        renderIo(out);
        out.append('\n');
        renderThrows(out);
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

    /**
     * The trace's socket and file operations, grouped by target and ranked by cost.
     * <p>
     * Rendered from every span rather than from the ones the tree had room for: a trace big enough
     * to truncate is exactly the trace whose I/O is worth totalling, and an accounting that silently
     * stopped at the four-hundredth span would be worse than none.
     */
    private void renderIo(StringBuilder out) {
        out.append(HEADING_IO).append('\n').append('\n');

        if (io.isEmpty()) {
            out.append(NO_IO_NOTE).append('\n');
            return;
        }

        long traceNanos = detail.trace().durationNanos();
        out.append(TraceAiFormat.count(io.operations(), "recorded operation"))
                .append(" · ").append(TraceAiFormat.bytes(io.bytes()))
                .append(" · ").append(TraceAiFormat.duration(io.totalNanos()))
                .append(" summed across threads (")
                .append(TraceAiFormat.percent(io.totalNanos(), traceNanos))
                .append(" of the trace window)")
                .append('\n').append('\n');

        List<TraceIoTarget> targets = io.targets();
        int rendered = Math.min(targets.size(), MAX_IO_TARGETS);
        for (int i = 0; i < rendered; i++) {
            renderIoTarget(out, targets.get(i));
        }

        if (targets.size() > rendered) {
            out.append(BULLET_PREFIX)
                    .append("(truncated: ")
                    .append(targets.size() - rendered)
                    .append(" further targets were omitted, out of ")
                    .append(targets.size())
                    .append(" — they are the cheapest, since this list is ranked by time)")
                    .append('\n');
        }
    }

    private void renderIoTarget(StringBuilder out, TraceIoTarget target) {
        out.append(BULLET_PREFIX)
                .append(target.direction().label())
                .append(' ').append(target.target())
                .append(DASH_SEPARATOR)
                .append(TraceAiFormat.count(target.operations(), "op"));

        // An fsync moves nothing, and printing "0 B, mean 0 B/op" against one would invite a reader
        // to conclude the recording lost the byte count rather than that there never was one.
        if (target.carriesBytes()) {
            out.append(" · bytes ").append(TraceAiFormat.bytes(target.bytes()))
                    .append(", mean ").append(TraceAiFormat.bytesPerOperation(target.meanBytes()));
        }

        out.append(" · time ").append(TraceAiFormat.duration(target.totalNanos()))
                .append(", mean ").append(TraceAiFormat.duration(target.meanNanos())).append("/op")
                .append(", max ").append(TraceAiFormat.duration(target.maxNanos()));

        if (target.smallOperations()) {
            out.append(SMALL_OPS_MARKER);
        }
        out.append('\n');
    }

    /**
     * Every throw the recording captured inside the trace, grouped by what was thrown.
     * <p>
     * The counts lead and the escaping ones sort first, because those are two different findings:
     * one is a cost the trace paid, the other is why something failed.
     */
    private void renderThrows(StringBuilder out) {
        out.append(HEADING_EXCEPTIONS).append('\n').append('\n');

        if (throwSummary.isEmpty()) {
            out.append(NO_THROWS_NOTE).append('\n');
            return;
        }

        out.append(TraceAiFormat.count(throwSummary.total(), "throw")).append(" recorded, ");
        if (throwSummary.escaped() == 0) {
            out.append("none of which escaped its span — every one was caught");
        } else {
            out.append(throwSummary.escaped()).append(" of which escaped its span");
        }
        out.append('.').append('\n').append('\n');

        List<TraceThrowGroup> groups = throwSummary.groups();
        int rendered = Math.min(groups.size(), MAX_THROW_GROUPS);
        for (int i = 0; i < rendered; i++) {
            renderThrowGroup(out, groups.get(i));
        }

        if (groups.size() > rendered) {
            out.append(BULLET_PREFIX)
                    .append("(truncated: ")
                    .append(groups.size() - rendered)
                    .append(" further distinct throws were omitted, out of ")
                    .append(groups.size())
                    .append(")")
                    .append('\n');
        }
    }

    private void renderThrowGroup(StringBuilder out, TraceThrowGroup group) {
        out.append(BULLET_PREFIX)
                .append(group.thrownClass())
                .append(" ×").append(group.count())
                .append(" (").append(group.eventType()).append(')');

        if (group.hasEscaped()) {
            out.append(ESCAPED_MARKER);
            // The count only when it is not the whole group: "×1 !escaped" already says all of it,
            // and "(1 of 1)" beside it reads as if something else were being counted.
            if (group.escaped() < group.count()) {
                out.append(" (").append(group.escaped()).append(" of ").append(group.count()).append(')');
            }
        }
        out.append('\n');

        if (group.message() != null && !group.message().isBlank()) {
            out.append(INDENT_UNIT).append("message: ").append(flatten(group.message())).append('\n');
        }
        renderThrowSites(out, group);
    }

    private void renderThrowSites(StringBuilder out, TraceThrowGroup group) {
        List<TraceThrowGroup.Site> sites = group.spans();
        if (sites.isEmpty()) {
            return;
        }

        out.append(INDENT_UNIT).append("thrown in: ");
        int rendered = Math.min(sites.size(), MAX_THROW_SITES);
        for (int i = 0; i < rendered; i++) {
            if (i > 0) {
                out.append(", ");
            }
            TraceThrowGroup.Site site = sites.get(i);
            out.append(site.spanName()).append(" ×").append(site.count());
        }
        if (sites.size() > rendered) {
            out.append(", and ").append(sites.size() - rendered).append(" further spans");
        }
        out.append('\n');
    }

    /**
     * A message reduced to one line and a readable length. An exception message can carry a whole
     * embedded stack trace, and a newline inside a bullet ends the bullet — a single throw could
     * otherwise take the shape of the document apart.
     */
    private static String flatten(String message) {
        String single = message.replaceAll("\\s+", " ").strip();
        return single.length() <= MAX_MESSAGE_CHARS
                ? single
                : single.substring(0, MAX_MESSAGE_CHARS) + "… (truncated)";
    }
}
