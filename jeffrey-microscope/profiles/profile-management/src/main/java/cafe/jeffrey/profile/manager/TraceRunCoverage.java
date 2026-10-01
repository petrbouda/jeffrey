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

import cafe.jeffrey.profile.manager.model.trace.TraceWindow;
import cafe.jeffrey.provider.profile.api.TraceSpanShape;

import java.util.ArrayList;
import java.util.List;

/**
 * What a folded run's lane draws in place of one tick per member: the trace window cut into equal
 * slices, each holding the fraction of it that at least one member was running.
 * <p>
 * Coverage rather than a count of members per slice, because the question the lane answers is
 * "when was this run holding the thread": a nine-millisecond write and a one-microsecond one are
 * one occurrence each, but only one of them kept anything waiting. Overlapping members are merged
 * first, so a slice is never more than fully covered.
 */
final class TraceRunCoverage {

    /** Enough to resolve a run's rhythm across a wide track, few enough to cost nothing on the wire. */
    static final int SLICES = 240;

    private static final double PRECISION = 1_000d;

    private TraceRunCoverage() {
    }

    /**
     * @param members the run's members in start order
     * @param window  the trace's window, which the slices divide
     */
    static List<Double> of(List<TraceSpanShape> members, TraceWindow window) {
        double[] covered = new double[SLICES];
        double sliceMicros = (double) window.lengthMicros() / SLICES;

        long mergedFrom = Long.MIN_VALUE;
        long mergedTo = Long.MIN_VALUE;
        for (TraceSpanShape member : members) {
            long from = member.startEpochMicros();
            long to = TraceSkeleton.endMicrosOf(member);
            if (from <= mergedTo) {
                mergedTo = Math.max(mergedTo, to);
                continue;
            }
            if (mergedTo > mergedFrom) {
                spread(covered, mergedFrom - window.startEpochMicros(), mergedTo - window.startEpochMicros(), sliceMicros);
            }
            mergedFrom = from;
            mergedTo = to;
        }
        if (mergedTo > mergedFrom) {
            spread(covered, mergedFrom - window.startEpochMicros(), mergedTo - window.startEpochMicros(), sliceMicros);
        }

        List<Double> fractions = new ArrayList<>(SLICES);
        for (double micros : covered) {
            double fraction = Math.min(1d, micros / sliceMicros);
            fractions.add(Math.round(fraction * PRECISION) / PRECISION);
        }
        return fractions;
    }

    /** Adds one merged interval, relative to the window start, to the slices it crosses. */
    private static void spread(double[] covered, long from, long to, double sliceMicros) {
        double cursor = Math.max(0, from);
        double end = Math.min(to, sliceMicros * SLICES);
        int slice = Math.min(SLICES - 1, (int) (cursor / sliceMicros));
        while (cursor < end && slice < SLICES) {
            double sliceEnd = Math.min(end, (slice + 1) * sliceMicros);
            covered[slice] += sliceEnd - cursor;
            cursor = sliceEnd;
            slice++;
        }
    }
}
