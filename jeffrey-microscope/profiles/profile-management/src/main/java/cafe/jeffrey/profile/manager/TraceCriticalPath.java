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
package cafe.jeffrey.profile.manager;

import cafe.jeffrey.provider.profile.api.TraceSpanShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How much of the trace's end-to-end duration each span is personally responsible for, in
 * microseconds keyed by span id. A span missing from the result contributed nothing: shortening
 * it would not have shortened the trace.
 * <p>
 * The walk runs backwards from the end of the trace. Within a span's window the last child to
 * finish is what held the span open, so that child owns the stretch it covers and the span owns
 * what is left between and around its children; recursing into each child in turn gives the
 * chain of work that actually determined the total. A child that ran entirely inside a
 * later-finishing sibling's window is therefore credited nothing at all — shortening it would
 * have changed no total, which is the whole point of drawing the path.
 * <p>
 * Children are taken to block their parent. That is true by construction on the parent's own
 * thread, and it is the intended reading of a parent that hands work off and waits for it. A
 * parent that forked a cross-thread child and never waited will see that child credited time it
 * did not really cost — telling the two apart needs the thread's state, not the span tree.
 * <p>
 * The roots are walked as the children of one window covering the whole trace, so a trace that
 * recorded more than one root — which correct instrumentation cannot produce, but a recording
 * that began mid-request can — still attributes every stretch of its own timeline. That outer
 * window owns nothing itself; the gaps between roots belong to no span.
 * <p>
 * Walked over every span, the members of folded runs included: a write that covers part of its
 * parent's window takes that stretch from the parent whether or not the reader is shown the write.
 * That is why it runs on {@link TraceSpanShape}s — the walk needs only windows and parentage.
 */
final class TraceCriticalPath {

    private TraceCriticalPath() {
    }

    /**
     * @param roots            the spans heading the tree, in start order
     * @param childrenByParent every other span under the parent it hangs from, in start order
     */
    static Map<Long, Long> micros(
            List<TraceSpanShape> roots, Map<Long, List<TraceSpanShape>> childrenByParent) {

        Map<Long, Long> critical = new HashMap<>();
        if (roots.isEmpty()) {
            return critical;
        }

        long from = Long.MAX_VALUE;
        long to = Long.MIN_VALUE;
        for (TraceSpanShape root : roots) {
            from = Math.min(from, root.startEpochMicros());
            to = Math.max(to, TraceSkeleton.endMicrosOf(root));
        }

        Deque<Window> pending = new ArrayDeque<>();
        attribute(roots, from, to, null, critical, pending);

        // Iterative for the same reason the tree traversal is: a trace deep enough to overflow the
        // stack must still render. The visited set also stops a parent cycle from walking forever.
        Set<Long> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            Window window = pending.pop();
            TraceSpanShape span = window.span();
            if (!visited.add(span.spanId())) {
                continue;
            }
            attribute(childrenByParent.getOrDefault(span.spanId(), List.of()),
                    window.from(), window.to(), span, critical, pending);
        }
        return critical;
    }

    /**
     * Splits one window between the span that owns it and the children that held it open, crediting
     * the owner with what no child covered and queueing each covering child for its own turn.
     * <p>
     * Children are read in the order they <em>finished</em>, latest first, which is what the walk is
     * actually asking: at any instant, whatever finishes last from here is what the owner is still
     * waiting on. Start order will not do — a child that began earlier can finish later, and reading
     * by start would credit the shorter sibling and skip the one that really held the window open.
     * <p>
     * The cursor only ever moves left, so a child starting at or after it was already covered by a
     * later-finishing sibling and is skipped entirely, as is one that ended before the window began.
     * A {@code null} owner is the outer trace window, which credits nobody.
     */
    private static void attribute(
            List<TraceSpanShape> children,
            long from,
            long to,
            TraceSpanShape owner,
            Map<Long, Long> critical,
            Deque<Window> pending) {

        // A copy: the caller's list is in start order, which the tree traversal draws from.
        List<TraceSpanShape> byEnd = new ArrayList<>(children);
        byEnd.sort(Comparator.comparingLong(TraceSkeleton::endMicrosOf).reversed());

        long cursor = to;
        for (TraceSpanShape child : byEnd) {
            long childStart = child.startEpochMicros();
            long childEnd = TraceSkeleton.endMicrosOf(child);
            if (childStart >= cursor || childEnd <= from) {
                continue;
            }
            long clampedStart = Math.max(childStart, from);
            long clampedEnd = Math.min(childEnd, cursor);
            if (owner != null && clampedEnd < cursor) {
                critical.merge(owner.spanId(), cursor - clampedEnd, Long::sum);
            }
            pending.push(new Window(child, clampedStart, clampedEnd));
            cursor = clampedStart;
        }
        if (owner != null && cursor > from) {
            critical.merge(owner.spanId(), cursor - from, Long::sum);
        }
    }

    /**
     * A span queued for attribution, with the stretch of its parent's window it was found to be
     * holding open — its own bounds clipped to what the parent had left to give.
     */
    private record Window(TraceSpanShape span, long from, long to) {
    }
}
