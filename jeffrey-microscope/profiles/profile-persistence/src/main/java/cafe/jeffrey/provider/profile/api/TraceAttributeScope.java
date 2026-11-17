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

/**
 * Whether the conditions of a search have to hold together on one span, or anywhere in the trace.
 * <p>
 * Not a preference — it is the question being asked, and the two have different answers. A trace
 * whose HTTP span carries {@code tenant=acme} and whose JDBC span carries {@code rows=41887}
 * matches {@link #TRACE} and does not match {@link #SPAN}. Attributes are per-carrier and are never
 * inherited down the tree, so a page that quietly picks one of these is wrong for whoever wanted
 * the other.
 * <p>
 * The scope applies to each carrier on its own terms — see {@link #grouping(TraceAttributeCarrier)}.
 * A search mixing span and notification conditions under {@link #SPAN} asks for every span condition
 * on one span and every notification condition on one notification, both inside one trace.
 */
public enum TraceAttributeScope {

    /** Each condition may be satisfied by a different span of the trace. */
    TRACE("trace_id", "trace_id"),

    /** Every condition has to be satisfied by the same single span. */
    SPAN("trace_id, span_id", "trace_id, notification_id");

    private final String spanGrouping;
    private final String notificationGrouping;

    TraceAttributeScope(String spanGrouping, String notificationGrouping) {
        this.spanGrouping = spanGrouping;
        this.notificationGrouping = notificationGrouping;
    }

    /**
     * The GROUP BY the matching query runs at, for one carrier. The whole difference between the two
     * scopes is this one clause: group by the trace and the conditions may land on different spans,
     * group by the span as well and they may not.
     * <p>
     * A notification is grouped by its own id rather than by the span it fired in, so {@link #SPAN}
     * reads "every span condition satisfied by one span, every notification condition satisfied by
     * one notification, all inside one trace". Grouping notifications by span instead would collapse
     * every notification of a trace that carried no span into one bucket, and silently answer a
     * different question than the one asked.
     */
    public String grouping(TraceAttributeCarrier carrier) {
        return switch (carrier) {
            case SPAN -> spanGrouping;
            case NOTIFICATION -> notificationGrouping;
        };
    }
}
