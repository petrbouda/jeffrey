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

import cafe.jeffrey.flamegraph.code.CodeAttribution.FocusCost;
import cafe.jeffrey.flamegraph.code.CodeAttribution.LineValue;
import cafe.jeffrey.flamegraph.code.CodeAttribution.MethodCost;
import cafe.jeffrey.flamegraph.code.CodeAttribution.MethodValue;
import cafe.jeffrey.flamegraph.code.CodeAttribution.TierValue;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the per-stack records a flamegraph is drawn from into a {@link CodeAttribution}.
 * <p>
 * Each record is one stack (root first) with the samples and weight taken on it, already filtered by
 * the query -- event type, window, idle stacks, span scope -- so this sees exactly what the graph
 * would. Hidden-class frames (lambda proxies, method-handle forms) are skipped, as the differential
 * graph skips them: they join a caller to its callee and exist only for the run that made them.
 */
public final class CodeAttributionBuilder implements RecordBuilder<FlamegraphRecord, CodeAttribution> {

    /** A line the recording did not keep is stored as 0 or a negative number. */
    private static final int FIRST_LINE = 1;

    private static final String UNKNOWN_SYMBOL = "<unknown>";

    private static final Comparator<MethodValue> BY_VALUE = Comparator
            .comparingLong(MethodValue::value).reversed()
            .thenComparing(MethodValue::method);

    private final FrameScope scope;
    private final boolean useWeight;
    private final List<FocusAccumulator> focus;
    private final Map<MethodKey, OwnAccumulator> own = new HashMap<>();
    private final Map<MethodKey, Long> inclusiveByMethod = new HashMap<>();
    private final Map<MethodKey, Long> entryPoints = new HashMap<>();

    private long total;
    private long chargedToOwnCode;

    /**
     * @param query     whose code is own and which methods to describe
     * @param useWeight count each record's weight (bytes, nanoseconds) rather than its samples
     */
    public CodeAttributionBuilder(CodeQuery query, boolean useWeight) {
        this.scope = query.scope();
        this.useWeight = useWeight;
        this.focus = query.focus().stream().map(FocusAccumulator::new).toList();
    }

    @Override
    public void onRecord(FlamegraphRecord record) {
        long value = useWeight ? record.weight() : record.samples();
        JfrStackTrace stackTrace = record.stackTrace();
        if (value == 0 || stackTrace == null) {
            return;
        }
        total += value;

        List<Step> steps = steps(stackTrace.frames());
        if (steps.isEmpty()) {
            return;
        }

        countInclusive(steps, value);
        if (scope.chargesCost()) {
            chargeOwn(steps, value);
        }
        for (FocusAccumulator accumulator : focus) {
            accumulator.add(steps, value, scope);
        }
    }

    @Override
    public CodeAttribution build() {
        List<MethodCost> ownMethods = own.values().stream()
                .map(OwnAccumulator::toCost)
                .sorted(Comparator.comparingLong(MethodCost::charged).reversed()
                        .thenComparing(Comparator.comparingLong(MethodCost::inclusive).reversed())
                        .thenComparing(MethodCost::method))
                .toList();
        return new CodeAttribution(
                total,
                chargedToOwnCode,
                ownMethods,
                sortedValues(entryPoints),
                sortedValues(inclusiveByMethod),
                focus.stream().map(FocusAccumulator::toCost).toList());
    }

    private void countInclusive(List<Step> steps, long value) {
        for (Map.Entry<MethodKey, Step> entry : deepestJavaOccurrences(steps).entrySet()) {
            inclusiveByMethod.merge(entry.getKey(), value, Long::sum);
        }
    }

    private void chargeOwn(List<Step> steps, long value) {
        Map<MethodKey, Step> ownOccurrences = new LinkedHashMap<>();
        int outermostOwn = -1;
        int deepestOwn = -1;
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (step.java() && scope.isOwn(step.key().className())) {
                ownOccurrences.put(step.key(), step);
                if (outermostOwn < 0) {
                    outermostOwn = i;
                }
                deepestOwn = i;
            }
        }
        if (deepestOwn < 0) {
            return;
        }

        chargedToOwnCode += value;
        entryPoints.merge(steps.get(outermostOwn).key(), value, Long::sum);
        for (Step occurrence : ownOccurrences.values()) {
            own.computeIfAbsent(occurrence.key(), OwnAccumulator::new).addInclusive(occurrence.frameType(), value);
        }

        Step charged = steps.get(deepestOwn);
        OwnAccumulator accumulator = own.get(charged.key());
        accumulator.addCharged(charged.line(), value);
        if (deepestOwn == steps.size() - 1) {
            accumulator.addSelf(value);
        } else {
            accumulator.addDelegated(steps.get(deepestOwn + 1).key(), value);
        }
    }

    /** Each distinct Java method of the stack with its deepest occurrence, root first. */
    private static Map<MethodKey, Step> deepestJavaOccurrences(List<Step> steps) {
        Map<MethodKey, Step> occurrences = new LinkedHashMap<>();
        for (Step step : steps) {
            if (step.java()) {
                occurrences.put(step.key(), step);
            }
        }
        return occurrences;
    }

    private static List<Step> steps(List<? extends JfrStackFrame> frames) {
        List<Step> steps = new ArrayList<>(frames.size());
        for (JfrStackFrame frame : frames) {
            JfrMethod method = frame.method();
            if (method == null || method.clazz().isHidden()) {
                continue;
            }
            FrameType frameType = FrameType.fromCode(frame.type());
            boolean java = frameType.isJavaFrame();
            String className = java && method.className() != null ? method.className() : "";
            String methodName = method.methodName() == null ? UNKNOWN_SYMBOL : method.methodName();
            steps.add(new Step(new MethodKey(className, methodName), frameType, java, frame.lineNumber()));
        }
        return steps;
    }

    private static List<MethodValue> sortedValues(Map<MethodKey, Long> values) {
        return values.entrySet().stream()
                .map(entry -> new MethodValue(entry.getKey(), entry.getValue()))
                .sorted(BY_VALUE)
                .toList();
    }

    private static List<LineValue> sortedLines(Map<Integer, Long> lines) {
        return lines.entrySet().stream()
                .map(entry -> new LineValue(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(LineValue::value).reversed()
                        .thenComparingInt(LineValue::line))
                .toList();
    }

    private static List<TierValue> sortedTiers(Map<FrameType, Long> tiers) {
        return tiers.entrySet().stream()
                .map(entry -> new TierValue(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(TierValue::value).reversed()
                        .thenComparing(TierValue::frameType))
                .toList();
    }

    private static void addLine(Map<Integer, Long> lines, int line, long value) {
        if (line >= FIRST_LINE) {
            lines.merge(line, value, Long::sum);
        }
    }

    /** One frame of a stack, as this reads it. */
    private record Step(MethodKey key, FrameType frameType, boolean java, int line) {
    }

    private static final class OwnAccumulator {

        private final MethodKey method;
        private final Map<Integer, Long> chargedLines = new HashMap<>();
        private final Map<MethodKey, Long> delegatedTo = new HashMap<>();
        private final Map<FrameType, Long> tiers = new EnumMap<>(FrameType.class);
        private long inclusive;
        private long self;
        private long charged;

        private OwnAccumulator(MethodKey method) {
            this.method = method;
        }

        private void addInclusive(FrameType frameType, long value) {
            inclusive += value;
            tiers.merge(frameType, value, Long::sum);
        }

        private void addCharged(int line, long value) {
            charged += value;
            addLine(chargedLines, line, value);
        }

        private void addSelf(long value) {
            self += value;
        }

        private void addDelegated(MethodKey callee, long value) {
            delegatedTo.merge(callee, value, Long::sum);
        }

        private MethodCost toCost() {
            return new MethodCost(method, inclusive, self, charged, sortedLines(chargedLines),
                    sortedValues(delegatedTo), sortedTiers(tiers));
        }
    }

    private static final class FocusAccumulator {

        private final MethodRef ref;
        private final MethodRef.Matcher matcher;
        private final Map<MethodKey, Long> matched = new HashMap<>();
        private final Map<MethodKey, Long> callers = new HashMap<>();
        private final Map<MethodKey, Long> callees = new HashMap<>();
        private final Map<MethodKey, Long> ownCallers = new HashMap<>();
        private final Map<Integer, Long> lines = new HashMap<>();
        private final Map<FrameType, Long> tiers = new EnumMap<>(FrameType.class);
        private long inclusive;
        private long self;

        private FocusAccumulator(MethodRef ref) {
            this.ref = ref;
            this.matcher = ref.matcher();
        }

        private void add(List<Step> steps, long value, FrameScope scope) {
            int outermost = -1;
            int innermost = -1;
            Set<MethodKey> matchedHere = new LinkedHashSet<>();
            for (int i = 0; i < steps.size(); i++) {
                Step step = steps.get(i);
                if (step.java() && matcher.matches(step.key())) {
                    if (outermost < 0) {
                        outermost = i;
                    }
                    innermost = i;
                    matchedHere.add(step.key());
                }
            }
            if (outermost < 0) {
                return;
            }

            inclusive += value;
            for (MethodKey key : matchedHere) {
                matched.merge(key, value, Long::sum);
            }

            Step inner = steps.get(innermost);
            addLine(lines, inner.line(), value);
            tiers.merge(inner.frameType(), value, Long::sum);
            if (innermost == steps.size() - 1) {
                self += value;
            } else {
                callees.merge(steps.get(innermost + 1).key(), value, Long::sum);
            }

            if (outermost > 0) {
                callers.merge(steps.get(outermost - 1).key(), value, Long::sum);
            }
            if (scope.chargesCost()) {
                nearestOwnAbove(steps, outermost, scope)
                        .ifPresent(caller -> ownCallers.merge(caller, value, Long::sum));
            }
        }

        private Optional<MethodKey> nearestOwnAbove(List<Step> steps, int index, FrameScope scope) {
            for (int i = index - 1; i >= 0; i--) {
                Step step = steps.get(i);
                if (step.java() && scope.isOwn(step.key().className()) && !matcher.matches(step.key())) {
                    return Optional.of(step.key());
                }
            }
            return Optional.empty();
        }

        private FocusCost toCost() {
            return new FocusCost(ref, sortedValues(matched), inclusive, self, sortedValues(callers),
                    sortedValues(callees), sortedValues(ownCallers), sortedLines(lines), sortedTiers(tiers));
        }
    }
}
