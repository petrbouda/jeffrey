/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.provider.profile.api;

/**
 * Whether the conditions of a search have to hold together on one span, or anywhere in the trace.
 * <p>
 * Not a preference — it is the question being asked, and the two have different answers. A trace
 * whose HTTP span carries {@code tenant=acme} and whose JDBC span carries {@code rows=41887}
 * matches {@link #TRACE} and does not match {@link #SPAN}. Attributes are per-span and are never
 * inherited down the tree, so a page that quietly picks one of these is wrong for whoever wanted
 * the other.
 */
public enum TraceAttributeScope {

    /** Each condition may be satisfied by a different span of the trace. */
    TRACE("trace_id"),

    /** Every condition has to be satisfied by the same single span. */
    SPAN("trace_id, span_id");

    private final String grouping;

    TraceAttributeScope(String grouping) {
        this.grouping = grouping;
    }

    /**
     * The GROUP BY the matching query runs at. The whole difference between the two scopes is this
     * one clause: group by the trace and the conditions may land on different spans, group by the
     * span as well and they may not.
     */
    public String grouping() {
        return grouping;
    }
}
