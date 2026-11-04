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

import cafe.jeffrey.profile.common.model.FrameType;

import java.util.List;

/**
 * What a recording measured of the code, method by method: the read a reader holding the source asks
 * for, built from the same per-stack aggregation a flamegraph is drawn from.
 * <p>
 * Three readings of one method's cost, each answering a different question:
 * <ul>
 *   <li><b>inclusive</b> -- samples whose stack holds the method anywhere, counted once per stack however
 *   often it recurses: how much happens while it is running;</li>
 *   <li><b>self</b> -- samples taken in the method itself, at the top of the stack;</li>
 *   <li><b>charged</b> -- samples whose deepest own frame is the method: its self time plus everything
 *   below it that is not own code (a library, the JDK, the JVM). The charged figures of all own
 *   methods add up to {@link #chargedToOwnCode()}, which is what makes them a ranking of the code that
 *   can be changed.</li>
 * </ul>
 *
 * @param total              every sample (or unit of weight) the query read
 * @param chargedToOwnCode   the part of {@code total} whose stack held at least one own frame
 * @param ownMethods         every own method, by charged cost, highest first; empty without own code
 * @param inclusiveByMethod  every Java method's inclusive cost, highest first -- what a reference that
 *                           matched nothing is compared against
 * @param focus              one answer per reference asked about, in the order asked
 */
public record CodeAttribution(
        long total,
        long chargedToOwnCode,
        List<MethodCost> ownMethods,
        List<MethodValue> inclusiveByMethod,
        List<FocusCost> focus) {

    public CodeAttribution {
        ownMethods = List.copyOf(ownMethods);
        inclusiveByMethod = List.copyOf(inclusiveByMethod);
        focus = List.copyOf(focus);
    }

    /** A method, or a frame below one, with the cost that went through it. */
    public record MethodValue(MethodKey method, long value) {
    }

    /** A source line of one method with the cost taken on it. */
    public record LineValue(int line, long value) {
    }

    /** How a method ran -- interpreted, compiled, inlined -- by the cost taken in each tier. */
    public record TierValue(FrameType frameType, long value) {
    }

    /**
     * One own method.
     *
     * @param chargedLines the method's lines its charged cost was taken on, highest first: the line
     *                     executing in it when the sample was taken, which is the call into whatever
     *                     ran below it, or the line itself when it was on top
     * @param delegatedTo  the frames directly below it that its charged cost went into, highest first;
     *                     what is left of {@code charged} is its {@code self}
     */
    public record MethodCost(
            MethodKey method,
            long inclusive,
            long self,
            long charged,
            List<LineValue> chargedLines,
            List<MethodValue> delegatedTo,
            List<TierValue> tiers) {

        public MethodCost {
            chargedLines = List.copyOf(chargedLines);
            delegatedTo = List.copyOf(delegatedTo);
            tiers = List.copyOf(tiers);
        }
    }

    /**
     * Everything measured about one reference.
     *
     * @param matchedFrames the runtime methods the reference matched, with each one's inclusive cost
     * @param callers       the frames directly above the outermost matched frame -- who calls it
     * @param callees       the frames directly below the innermost matched frame -- what it calls;
     *                      what is left of {@code inclusive} is {@code self}
     * @param ownCallers    the nearest own frame above it on each stack, for a method that is not own
     *                      itself: which code of the reader's reaches it; empty without own code
     * @param lines         the lines of the innermost matched frame the cost was taken on
     */
    public record FocusCost(
            MethodRef ref,
            List<MethodValue> matchedFrames,
            long inclusive,
            long self,
            List<MethodValue> callers,
            List<MethodValue> callees,
            List<MethodValue> ownCallers,
            List<LineValue> lines,
            List<TierValue> tiers) {

        public FocusCost {
            matchedFrames = List.copyOf(matchedFrames);
            callers = List.copyOf(callers);
            callees = List.copyOf(callees);
            ownCallers = List.copyOf(ownCallers);
            lines = List.copyOf(lines);
            tiers = List.copyOf(tiers);
        }

        /** Whether any sample ran the referenced code. */
        public boolean seen() {
            return inclusive > 0;
        }
    }
}
