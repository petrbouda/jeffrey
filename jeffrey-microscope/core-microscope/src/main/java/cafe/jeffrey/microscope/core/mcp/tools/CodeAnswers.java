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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.flamegraph.code.CodeAttribution.LineValue;
import cafe.jeffrey.flamegraph.code.CodeAttribution.MethodValue;
import cafe.jeffrey.flamegraph.code.CodeAttribution.TierValue;
import cafe.jeffrey.flamegraph.code.MethodKey;
import cafe.jeffrey.flamegraph.code.MethodRef;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.model.GraphType;
import cafe.jeffrey.microscope.model.SpanScope;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.common.config.GraphComponents;
import cafe.jeffrey.profile.common.config.GraphParameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What the code tools ({@code code_hotspots}, {@code code_method}) and {@code compare_methods} share:
 * the unit a figure counts in, the rows that name a frame with its share, and the graph parameters a
 * code read is made with.
 * <p>
 * Every frame travels as {@code Class#method}, the spelling the flamegraph exports use, and every
 * reference the reader passes in comes back with the runtime names it matched, so a reader holding
 * the source can always see which methods of theirs a figure counts.
 */
final class CodeAnswers {

    /** Characters the UI's flamegraph search reads as regular-expression syntax. */
    private static final Pattern SEARCH_METACHARACTERS = Pattern.compile("[\\\\^$.|?*+()\\[\\]{}]");
    /** The match, preceded by a backslash: {@code $0} is the matched character. */
    private static final String ESCAPED_MATCH = Matcher.quoteReplacement("\\") + "$0";
    /** A dot of a class name as the reader wrote it: a package dot, or a nested class's, a {@code $} at runtime. */
    private static final String SOURCE_DOT = "[.$]";
    private static final String CLASS_DOT = ".";
    private static final String METHOD_SEPARATOR = "#";
    /** At most this many methods an answer names as the nearest to a reference nothing ran. */
    static final int MAX_NEAREST = 10;
    private static final char NESTED_SEPARATOR = '$';
    private static final char SOURCE_SEPARATOR = '.';

    /** The {@code reference} of a row naming one of the reader's methods. */
    static final String REFERENCE_DESCRIPTION = "The method as its source spells it - Outer.Inner#method, a lambda "
            + "body by the method it is written in - which code_method and compare_methods take";
    private static final double PERCENT = 100.0;
    private static final double HUNDREDTHS = 100.0;

    /** Event types whose weight is bytes; every other weighed type weighs nanoseconds. */
    private static final Set<Type> BYTE_WEIGHTED = Set.of(
            Type.OBJECT_ALLOCATION_SAMPLE, Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
            Type.OBJECT_ALLOCATION_OUTSIDE_TLAB, Type.MALLOC, Type.NATIVE_LEAK);

    private CodeAnswers() {
    }

    /** What a figure counts. */
    enum ValueUnit {
        SAMPLES,
        BYTES,
        NANOSECONDS;

        static ValueUnit of(Type type, boolean weighted) {
            if (!weighted) {
                return SAMPLES;
            }
            return BYTE_WEIGHTED.contains(type) || type.isAllocationEvent() ? BYTES : NANOSECONDS;
        }
    }

    /** What a code read found. */
    enum CodeStatus {
        /** The read found the code it was asked about. */
        OK,
        /** The profile recorded no samples with stacks of the asked type, so nothing could be read. */
        NOT_RECORDED,
        /**
         * No sample's stack ran the referenced code. A measurement, not an error: in this profile the
         * code was not hot, was never called, or runs under a name {@code nearestFrames} suggests.
         */
        NOT_SEEN,
        /**
         * The event type was recorded without stack traces, so its events cannot name a line of code -- the
         * way Jeffrey records its HTTP and gRPC exchanges and its trace spans.
         */
        NO_STACKS
    }

    /**
     * A frame next to the code asked about, or below an own method, with the cost that went through it.
     */
    record FrameShare(
            @McpDescription("The frame, as Class#method for Java and the bare symbol for native code")
            String frame,
            long value,
            @McpDescription("Its share of the method's own figure this row is part of, in percent")
            double sharePct) {
    }

    /** A source line of a method with the cost taken on it. */
    record LineShare(
            int line,
            long value,
            @McpDescription("Its share of the method's figure, in percent")
            double sharePct) {
    }

    /** How a method ran, by the cost taken in each tier. */
    record TierShare(
            @McpDescription("JIT_COMPILED, C1_COMPILED, INLINED or INTERPRETED")
            String frameType,
            double sharePct) {
    }

    /** A runtime method a reference matched, with its inclusive cost. */
    record MatchedFrame(
            @McpDescription("The runtime name the reference matched, as Class#method")
            String frame,
            long inclusive) {
    }

    static List<FrameShare> frames(List<MethodValue> values, long of, int limit) {
        return values.stream()
                .limit(limit)
                .map(value -> new FrameShare(value.method().displayName(), value.value(), pct(value.value(), of)))
                .toList();
    }

    static List<LineShare> lines(List<LineValue> values, long of, int limit) {
        return values.stream()
                .limit(limit)
                .map(value -> new LineShare(value.line(), value.value(), pct(value.value(), of)))
                .toList();
    }

    static List<TierShare> tiers(List<TierValue> values) {
        long total = values.stream().mapToLong(TierValue::value).sum();
        return values.stream()
                .map(value -> new TierShare(value.frameType().name(), pct(value.value(), total)))
                .toList();
    }

    static List<MatchedFrame> matched(List<MethodValue> values, int limit) {
        return values.stream()
                .limit(limit)
                .map(value -> new MatchedFrame(value.method().displayName(), value.value()))
                .toList();
    }

    /** A share in percent, to two decimals; zero of nothing is zero. */
    static double pct(double part, double whole) {
        if (whole <= 0) {
            return 0.0;
        }
        return Math.round(part * PERCENT * HUNDREDTHS / whole) / HUNDREDTHS;
    }

    /**
     * The UI flamegraph search that marks this method's frames: the reference with every character
     * the search would read as regular-expression syntax escaped, so {@code Outer$Inner} is found as
     * written, and each dot of the class matching a {@code $} as well, so the source spelling
     * {@code Outer.Inner} finds the runtime's {@code Outer$Inner} frames.
     */
    static String search(MethodRef ref) {
        String className = Arrays.stream(ref.className().split(Pattern.quote(CLASS_DOT), -1))
                .map(CodeAnswers::escaped)
                .collect(Collectors.joining(SOURCE_DOT));
        return ref.wholeClass() ? className : className + escaped(METHOD_SEPARATOR + ref.methodName());
    }

    /** The search for one runtime frame, escaped as {@link #search(MethodRef)} escapes a reference. */
    static String search(MethodKey frame) {
        return escaped(frame.displayName());
    }

    /**
     * Sampled methods whose name says they may be the one a reference meant: those of the same class or a
     * class nested in it, and those with the same method name. Class context ranks before unrelated
     * namesakes; within that context the same method name ranks first, then inclusive cost. Classes are
     * compared as source spells them, so a
     * reference written {@code Outer.Inner} finds {@code Outer$Inner}'s methods, one that left out the nested
     * class ({@code Outer#method}) finds the nested method of that name, and one that left out the package
     * ({@code Outer.Inner#method}) finds the class in whatever package it is.
     */
    static List<MethodValue> nearest(MethodRef ref, List<MethodValue> inclusiveByMethod) {
        Map<Proximity, List<MethodValue>> byProximity = new EnumMap<>(Proximity.class);
        for (MethodValue value : inclusiveByMethod) {
            if (value.method().className().isEmpty()) {
                continue;
            }
            Proximity proximity = proximity(ref, value.method());
            if (proximity != Proximity.UNRELATED) {
                byProximity.computeIfAbsent(proximity, _ -> new ArrayList<>()).add(value);
            }
        }
        List<MethodValue> ranked = byProximity.values().stream().flatMap(List::stream)
                .filter(distinctMethods())
                .toList();
        if (ranked.size() <= MAX_NEAREST) {
            return ranked;
        }
        List<MethodValue> shown = new ArrayList<>(ranked.subList(0, MAX_NEAREST));
        // A class with many sampled methods must not push out the method itself, written in another class.
        Set<MethodKey> listed = new HashSet<>();
        shown.forEach(value -> listed.add(value.method()));
        List<MethodValue> namesakes = byProximity.getOrDefault(Proximity.SAME_METHOD, List.of());
        if (!namesakes.isEmpty() && namesakes.stream().noneMatch(value -> listed.contains(value.method()))) {
            shown.set(MAX_NEAREST - 1, namesakes.getFirst());
        }
        return List.copyOf(shown);
    }

    /** A unique best source reference from the complete pool, never from a capped suggestion list. */
    static Optional<MethodRef> correction(MethodRef ref, List<MethodValue> inclusiveByMethod) {
        Map<Proximity, Set<MethodRef>> candidates = new EnumMap<>(Proximity.class);
        for (MethodValue value : inclusiveByMethod) {
            if (value.method().className().isEmpty()) {
                continue;
            }
            MethodRef candidate = MethodRef.of(value.method());
            Proximity proximity = proximity(ref, value.method());
            if (proximity == Proximity.SAME_METHOD_AND_CLASS || proximity == Proximity.SAME_METHOD_AND_NESTED_CLASS) {
                candidates.computeIfAbsent(proximity, _ -> new LinkedHashSet<>()).add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Set<MethodRef> best = candidates.values().iterator().next();
        return best.size() == 1 ? Optional.of(best.iterator().next()) : Optional.empty();
    }

    /** In preference order; enum ordering keeps each original inclusive-cost ranking stable. */
    private enum Proximity {
        SAME_METHOD_AND_CLASS,
        SAME_METHOD_AND_NESTED_CLASS,
        SAME_CLASS,
        NESTED_CLASS,
        SAME_METHOD,
        UNRELATED
    }

    private static Proximity proximity(MethodRef ref, MethodKey frame) {
        String refClass = sourceClass(ref.className());
        String binaryName = frame.className();
        int packageEnd = binaryName.lastIndexOf(SOURCE_SEPARATOR);
        String candidateClass = sourceClass(binaryName);
        String simpleClass = sourceClass(binaryName.substring(packageEnd + 1));
        boolean sameName = ref.methodName() != null && ref.methodName().equals(MethodRef.of(frame).methodName());
        if (candidateClass.equals(refClass) || simpleClass.equals(refClass)) {
            return sameName ? Proximity.SAME_METHOD_AND_CLASS : Proximity.SAME_CLASS;
        }
        for (int boundary = binaryName.indexOf(NESTED_SEPARATOR, packageEnd + 1); boundary >= 0;
             boundary = binaryName.indexOf(NESTED_SEPARATOR, boundary + 1)) {
            if (sourceClass(binaryName.substring(0, boundary)).equals(refClass)
                    || sourceClass(binaryName.substring(packageEnd + 1, boundary)).equals(refClass)) {
                return sameName ? Proximity.SAME_METHOD_AND_NESTED_CLASS : Proximity.NESTED_CLASS;
            }
        }
        return sameName ? Proximity.SAME_METHOD : Proximity.UNRELATED;
    }

    /** Keeps the first value of each method, for a pool gathered from several reads. */
    private static Predicate<MethodValue> distinctMethods() {
        Set<MethodKey> seen = new HashSet<>();
        return value -> seen.add(value.method());
    }

    private static String sourceClass(String className) {
        return className.replace(NESTED_SEPARATOR, SOURCE_SEPARATOR);
    }

    private static String escaped(String name) {
        return SEARCH_METACHARACTERS.matcher(name).replaceAll(ESCAPED_MATCH);
    }

    /**
     * The parameters of a code read: the stacks a thread-aggregated flamegraph of this type, range and
     * filters would draw, optionally inside one span or operation.
     */
    static GraphParameters params(
            Type type, RelativeTimeRange timeRange, boolean weighted, boolean excludeIdle, SpanScope spanScope) {
        return GraphParameters.builder()
                .withEventType(type)
                .withTimeRange(timeRange)
                .withThreads(List.of())
                .withThreadMode(false)
                .withUseWeight(weighted)
                .withExcludeNonJavaSamples(false)
                .withExcludeIdleSamples(excludeIdle)
                .withOnlyUnsafeAllocationSamples(false)
                .withParseLocation(true)
                .withGraphType(GraphType.PRIMARY)
                .withGraphComponents(GraphComponents.FLAMEGRAPH_ONLY)
                .withSpanScope(spanScope)
                .build();
    }

    /** Weighed by default exactly where the flamegraph managers weigh: allocation and blocking. */
    static boolean weightedByDefault(Type type) {
        return type.isAllocationEvent() || type.isBlockingEvent();
    }

    /** The prefixes as given, trimmed, blanks and repeats dropped, and bounded ({@link FramePrefixes}). */
    static List<String> prefixes(List<String> framePrefixes) {
        return FramePrefixes.of(framePrefixes);
    }
}
