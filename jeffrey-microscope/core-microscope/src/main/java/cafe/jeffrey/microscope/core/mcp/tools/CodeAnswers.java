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

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        NOT_SEEN
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
     * written.
     */
    static String search(MethodRef ref) {
        return escaped(ref.displayName());
    }

    /** The search for one runtime frame, escaped as {@link #search(MethodRef)} escapes a reference. */
    static String search(MethodKey frame) {
        return escaped(frame.displayName());
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

    /** The prefixes as given, trimmed, blanks and repeats dropped. */
    static List<String> prefixes(List<String> framePrefixes) {
        if (framePrefixes == null) {
            return List.of();
        }
        return framePrefixes.stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
    }
}
