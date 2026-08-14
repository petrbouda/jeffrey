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

import java.util.Objects;

/**
 * What identifies a trace type — an operation — across every read that drills into one.
 * <p>
 * The name alone does not identify it. An inbound {@code GET /orders} and an outbound call to the
 * same path are named identically by the same convention, and a hand-written span may be named like
 * either; they are different operations, told apart by the role the root played and by the
 * instrumentation that opened the trace. Grouping on the name alone merged them into one row whose
 * kind badge was whichever value the aggregate happened to sample.
 * <p>
 * Travelling as one record rather than three loose strings is what keeps that from drifting: every
 * query that filters a trace type takes this, so none of them can be written to match on less than
 * the whole of it.
 *
 * @param name       the root span's name, e.g. {@code GET /orders}
 * @param kind       the role the root played, a {@code SpanKind} name such as {@code SERVER}
 * @param eventType  the event type that opened the trace, e.g. {@code jeffrey.HttpServerExchange}
 */
public record TraceOperationId(String name, String kind, String eventType) {

    public TraceOperationId {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
    }
}
