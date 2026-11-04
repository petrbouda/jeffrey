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
 * @param groupValue  the value of the field the read groups by; null when it groups by nothing or the
 *                    event carried no such field
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
        Long traceId,
        TraceOperationId operation,
        long count,
        long totalNanos,
        long maxNanos) {
}
