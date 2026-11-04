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
import cafe.jeffrey.provider.profile.api.CallSiteRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.TraceOperationId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads grouped event rows into {@link CallSites}: each row's events charged to the deepest own frame
 * on their stack, together with the line that frame was on -- the line of the reader's code that issued
 * the statement, the call or the lock. Rows that belong to a trace are also counted per trace, which is
 * what shows one request issuing the same statement many times.
 */
public final class CallSiteBuilder implements RecordBuilder<CallSiteRecord, CallSites> {

    private static final int UNKNOWN_LINE = 0;

    private final FrameScope scope;
    private final Map<SiteKey, SiteAccumulator> sites = new HashMap<>();
    private final Map<EntryKey, Long> entries = new HashMap<>();

    private long totalEvents;
    private long totalNanos;
    private long unattributedEvents;

    public CallSiteBuilder(FrameScope scope) {
        this.scope = scope;
    }

    @Override
    public void onRecord(CallSiteRecord record) {
        totalEvents += record.count();
        totalNanos += record.totalNanos();
        List<PathFrame> path = ownPath(record.stackTrace());
        if (path.isEmpty()) {
            unattributedEvents += record.count();
            return;
        }
        sites.computeIfAbsent(new SiteKey(path, record.groupValue()), _ -> new SiteAccumulator()).add(record);
        if (record.operation() != null) {
            outermostOwn(record.stackTrace()).ifPresent(method ->
                    entries.merge(new EntryKey(record.operation(), method), record.count(), Long::sum));
        }
    }

    @Override
    public CallSites build() {
        List<CallSite> built = sites.entrySet().stream()
                .map(entry -> entry.getValue().toSite(entry.getKey()))
                .sorted(Comparator.comparingLong(CallSite::totalNanos).reversed()
                        .thenComparing(Comparator.comparingLong(CallSite::count).reversed())
                        .thenComparing(CallSite::method)
                        .thenComparingInt(CallSite::line))
                .toList();
        List<OperationEntry> operationEntries = entries.entrySet().stream()
                .map(entry -> new OperationEntry(entry.getKey().operation(), entry.getKey().method(), entry.getValue()))
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

    private record SiteKey(List<PathFrame> path, String groupValue) {
    }

    private static final class SiteAccumulator {

        private final Map<Long, Long> perTrace = new HashMap<>();
        private long count;
        private long totalNanos;
        private long maxNanos;

        private void add(CallSiteRecord record) {
            count += record.count();
            totalNanos += record.totalNanos();
            maxNanos = Math.max(maxNanos, record.maxNanos());
            if (record.traceId() != null) {
                perTrace.merge(record.traceId(), record.count(), Long::sum);
            }
        }

        private CallSite toSite(SiteKey key) {
            List<Map.Entry<Long, Long>> byCount = new ArrayList<>(perTrace.entrySet());
            byCount.sort(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder())
                    .thenComparing(Map.Entry.comparingByKey()));
            long max = byCount.isEmpty() ? 0 : byCount.getFirst().getValue();
            List<Long> exemplars = byCount.stream()
                    .limit(CallSite.MAX_EXEMPLARS)
                    .map(Map.Entry::getKey)
                    .toList();
            PathFrame site = key.path().getFirst();
            return new CallSite(site.method(), site.line(), key.path(), key.groupValue(), count, totalNanos, maxNanos,
                    perTrace.size(), max, median(perTrace.values()), exemplars);
        }

        private static double median(Collection<Long> values) {
            if (values.isEmpty()) {
                return 0.0;
            }
            List<Long> sorted = values.stream().sorted().toList();
            int middle = sorted.size() / 2;
            return sorted.size() % 2 == 1
                    ? sorted.get(middle)
                    : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        }
    }
}
