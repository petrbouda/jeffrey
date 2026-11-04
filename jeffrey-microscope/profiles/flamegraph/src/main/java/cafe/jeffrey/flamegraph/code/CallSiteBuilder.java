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

package cafe.jeffrey.flamegraph.code;

import cafe.jeffrey.flamegraph.code.CallSites.CallSite;
import cafe.jeffrey.flamegraph.code.CallSites.OperationEntry;
import cafe.jeffrey.flamegraph.code.CallSites.PathFrame;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.provider.profile.api.CallSiteReader;
import cafe.jeffrey.provider.profile.api.CallSiteRow;
import cafe.jeffrey.provider.profile.api.OperationStackRow;
import cafe.jeffrey.provider.profile.api.TraceOperationId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a call-site read into {@link CallSites}, by the protocol {@link CallSiteReader} names: each distinct
 * stack is charged to its own path — the deepest own frame and the own frames that called it, with the
 * lines they were on, the line of the reader's code that issued the statement, the call or the lock — and
 * the database's rows per path and grouping value, per-trace figures included (what shows one request
 * issuing the same statement many times), become the sites. What is held here is bounded by the distinct
 * stacks and sites, never by the events or the traces.
 */
public final class CallSiteBuilder implements CallSiteReader<CallSites> {

    private static final int UNKNOWN_LINE = 0;

    private final FrameScope scope;
    private final Map<List<PathFrame>, Integer> pathIds = new HashMap<>();
    private final List<List<PathFrame>> paths = new ArrayList<>();
    private final Set<Long> attributedStacks = new HashSet<>();
    private final List<CallSite> sites = new ArrayList<>();
    private final Map<EntryKey, Long> outermostOwnEntries = new HashMap<>();
    private final Map<TraceOperationId, EntryTrunk> trunks = new HashMap<>();

    private long totalEvents;
    private long totalNanos;
    private long unattributedEvents;

    public CallSiteBuilder(FrameScope scope) {
        this.scope = scope;
    }

    @Override
    public int pathOf(JfrStackTrace stackTrace) {
        List<PathFrame> path = ownPath(stackTrace);
        if (path.isEmpty()) {
            return UNATTRIBUTED;
        }
        attributedStacks.add(stackTrace.id());
        return pathIds.computeIfAbsent(path, newPath -> {
            paths.add(newPath);
            return paths.size() - 1;
        });
    }

    @Override
    public void onSite(CallSiteRow row) {
        totalEvents += row.count();
        totalNanos += row.totalNanos();
        if (row.pathId() == UNATTRIBUTED) {
            unattributedEvents += row.count();
            return;
        }
        List<PathFrame> path = paths.get(row.pathId());
        PathFrame site = path.getFirst();
        sites.add(new CallSite(site.method(), site.line(), path, row.groupValue(), row.groupValueChars(),
                row.count(), row.totalNanos(), row.maxNanos(), row.traces(), row.perTraceMax(), row.perTraceMedian(),
                row.exemplarTraceIds()));
    }

    @Override
    public void onOperationStack(OperationStackRow row) {
        if (!attributedStacks.contains(row.stackTrace().id())) {
            return;
        }
        outermostOwn(row.stackTrace()).ifPresent(method ->
                outermostOwnEntries.merge(new EntryKey(row.operation(), method), row.count(), Long::sum));
        trunks.computeIfAbsent(row.operation(), _ -> new EntryTrunk())
                .add(javaFrames(row.stackTrace()), row.count());
    }

    @Override
    public CallSites build() {
        List<CallSite> built = sites.stream()
                .sorted(Comparator.comparingLong(CallSite::totalNanos).reversed()
                        .thenComparing(Comparator.comparingLong(CallSite::count).reversed())
                        .thenComparing(CallSite::method)
                        .thenComparingInt(CallSite::line))
                .toList();
        List<OperationEntry> operationEntries = operationEntries().stream()
                .sorted(Comparator.comparingLong(OperationEntry::events).reversed()
                        .thenComparing(OperationEntry::method))
                .toList();
        return new CallSites(totalEvents, totalNanos, unattributedEvents, built, operationEntries);
    }

    /**
     * The own Java frames of the stack from the deepest up, at most {@link CallSite#MAX_OWN_PATH}, a method
     * recursing into itself kept once; hidden-class frames skipped. Empty when the stack holds no own code.
     */
    private List<PathFrame> ownPath(JfrStackTrace stackTrace) {
        if (stackTrace == null || !scope.chargesCost()) {
            return List.of();
        }
        List<PathFrame> path = new ArrayList<>();
        List<? extends JfrStackFrame> frames = stackTrace.frames();
        for (int i = frames.size() - 1; i >= 0 && path.size() < CallSite.MAX_OWN_PATH; i--) {
            JfrStackFrame frame = frames.get(i);
            JfrMethod method = frame.method();
            if (method == null || method.clazz().isHidden() || method.className() == null
                    || method.methodName() == null) {
                continue;
            }
            if (!FrameType.fromCode(frame.type()).isJavaFrame() || !scope.isOwn(method.className())) {
                continue;
            }
            MethodKey key = new MethodKey(method.className(), method.methodName());
            if (!path.isEmpty() && path.getLast().method().equals(key)) {
                continue;
            }
            path.add(new PathFrame(key, frame.lineNumber() > 0 ? frame.lineNumber() : UNKNOWN_LINE));
        }
        return path;
    }

    /**
     * Each operation's entry point on the trunk its statements' stacks share ({@link EntryTrunk}), or, for an
     * operation whose trunk reaches no own code, each stack's outermost own frame.
     */
    private List<OperationEntry> operationEntries() {
        List<OperationEntry> entries = new ArrayList<>();
        for (Map.Entry<TraceOperationId, EntryTrunk> operation : trunks.entrySet()) {
            List<EntryTrunk.Entry> onTrunk = operation.getValue().entries();
            if (!onTrunk.isEmpty()) {
                onTrunk.forEach(entry ->
                        entries.add(new OperationEntry(operation.getKey(), entry.method(), entry.value())));
                continue;
            }
            outermostOwnEntries.forEach((key, events) -> {
                if (key.operation().equals(operation.getKey())) {
                    entries.add(new OperationEntry(key.operation(), key.method(), events));
                }
            });
        }
        return entries;
    }

    /** The Java frames of the stack, root first, each marked own or not; hidden-class frames skipped. */
    private List<EntryTrunk.Frame> javaFrames(JfrStackTrace stackTrace) {
        List<EntryTrunk.Frame> frames = new ArrayList<>();
        for (JfrStackFrame frame : stackTrace.frames()) {
            JfrMethod method = frame.method();
            if (method == null || method.clazz().isHidden() || method.className() == null
                    || method.methodName() == null || !FrameType.fromCode(frame.type()).isJavaFrame()) {
                continue;
            }
            frames.add(new EntryTrunk.Frame(new MethodKey(method.className(), method.methodName()),
                    scope.isOwn(method.className())));
        }
        return frames;
    }

    /** The shallowest own Java frame of the stack, skipping hidden-class frames. */
    private Optional<MethodKey> outermostOwn(JfrStackTrace stackTrace) {
        for (JfrStackFrame frame : stackTrace.frames()) {
            JfrMethod method = frame.method();
            if (method == null || method.clazz().isHidden() || method.className() == null
                    || method.methodName() == null) {
                continue;
            }
            if (FrameType.fromCode(frame.type()).isJavaFrame() && scope.isOwn(method.className())) {
                return Optional.of(new MethodKey(method.className(), method.methodName()));
            }
        }
        return Optional.empty();
    }

    private record EntryKey(TraceOperationId operation, MethodKey method) {
    }
}
