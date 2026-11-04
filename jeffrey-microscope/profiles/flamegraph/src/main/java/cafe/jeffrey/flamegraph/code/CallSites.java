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

import cafe.jeffrey.provider.profile.api.TraceOperationId;

import java.util.List;

/**
 * Where events of one type were issued from in the application's own code: each event's deepest own
 * frame, split by the value of one of its fields, with how often and how long, and -- for events that
 * belong to traces -- how many times one request issued them.
 *
 * @param totalEvents        every event read
 * @param totalNanos         their summed duration
 * @param unattributedEvents the events whose stack held no own frame
 * @param sites              the call sites, longest total first
 * @param operationEntries   for events issued inside traced requests, the outermost own frame of their
 *                           stacks per operation -- the handler the request entered the code through,
 *                           read from the events' own stacks; most events first
 */
public record CallSites(
        long totalEvents,
        long totalNanos,
        long unattributedEvents,
        List<CallSite> sites,
        List<OperationEntry> operationEntries) {

    public CallSites {
        sites = List.copyOf(sites);
        operationEntries = List.copyOf(operationEntries);
    }

    /**
     * The outermost own frame on the stacks of the events one operation's requests issued.
     *
     * @param events how many of its events had that frame outermost
     */
    public record OperationEntry(TraceOperationId operation, MethodKey method, long events) {
    }

    /**
     * One frame of the application's own code on the path to a call site, with the line it was on.
     *
     * @param line 0 when the recording kept no line
     */
    public record PathFrame(MethodKey method, int line) {
    }

    /**
     * One own path and one group value.
     *
     * @param line          the line the own frame was on when the event was committed; 0 when unknown
     * @param ownPath       the deepest own frame and the own frames that called it, deepest first, at most
     *                      {@link #MAX_OWN_PATH}: an application's own database or HTTP wrapper is the
     *                      deepest frame of every statement, and its callers say which code issued this one
     * @param groupValue    the grouping field's value; null when not grouped or the field was absent
     * @param traces        how many distinct traces issued it; 0 for events outside traces
     * @param perTraceMax   the most times one trace issued it
     * @param perTraceMedian the median times per trace that issued it at all
     * @param exemplarTraceIds the traces that issued it most often, at most {@link #MAX_EXEMPLARS}
     */
    public record CallSite(
            MethodKey method,
            int line,
            List<PathFrame> ownPath,
            String groupValue,
            long count,
            long totalNanos,
            long maxNanos,
            int traces,
            long perTraceMax,
            double perTraceMedian,
            List<Long> exemplarTraceIds) {

        /** How many of the busiest traces a call site names. */
        public static final int MAX_EXEMPLARS = 3;

        /** How many own frames a call site's path holds, the deepest first. */
        public static final int MAX_OWN_PATH = 3;

        public CallSite {
            ownPath = List.copyOf(ownPath);
            exemplarTraceIds = List.copyOf(exemplarTraceIds);
        }
    }
}
