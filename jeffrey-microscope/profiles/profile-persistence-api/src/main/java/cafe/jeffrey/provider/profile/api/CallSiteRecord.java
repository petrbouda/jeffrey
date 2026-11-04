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

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;

/**
 * Events of one type that share a stack, a value of one of their fields and a trace: how many there
 * were and how long they took. The unit a call-site read is built from.
 *
 * @param stackTrace  the stack the events were committed on, root first
 * @param groupValue  the value of the field the read groups by, cut to {@link #MAX_GROUP_VALUE_CHARS}
 *                    characters; null when it groups by nothing or the event carried no such field
 * @param groupValueChars the value's full length; null with the value
 * @param groupKey    what tells two values apart past the cut (a digest of the whole value); null with
 *                    the value
 * @param traceId     the trace the events belong to; null for an event outside any trace
 * @param operation   the operation that trace is a request of -- its root span's name, kind and event
 *                    type; null outside a trace
 * @param count       how many events
 * @param totalNanos  their summed duration, in nanoseconds; 0 for instant events
 * @param maxNanos    the longest of them, in nanoseconds
 */
public record CallSiteRecord(
        JfrStackTrace stackTrace,
        String groupValue,
        Integer groupValueChars,
        String groupKey,
        Long traceId,
        TraceOperationId operation,
        long count,
        long totalNanos,
        long maxNanos) {

    /**
     * How much of a grouping value a read hands over: enough to name a statement or recognise its SQL. The
     * rest is counted, not carried — a call-site read over a large recording groups every distinct value,
     * and a whole SQL text per group was held in memory before an answer cut it anyway.
     */
    public static final int MAX_GROUP_VALUE_CHARS = 200;
}
