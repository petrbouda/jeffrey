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

package cafe.jeffrey.jfr.events.trace;

/**
 * What role a span plays in the operation it describes — in practice, whether the time it covers
 * was the process's own work or time spent waiting on something else.
 * <p>
 * The names are OpenTelemetry's, so the concept reads the same to anyone who has used a tracer
 * before, but the set is deliberately smaller. OpenTelemetry also defines {@code PRODUCER} and
 * {@code CONSUMER}, whose purpose is to pair a span in one process with its counterpart in
 * another; a trace assembled from a single JVM recording has no such counterpart to pair with.
 */
public enum SpanKind {

    /**
     * A span covering work the process performs for itself — the default for an operation that
     * neither receives nor issues a remote call.
     */
    INTERNAL,

    /** Handling an inbound request, e.g. an HTTP or gRPC server exchange. */
    SERVER,

    /** Issuing an outbound request and waiting for its response, e.g. an HTTP call or a SQL query. */
    CLIENT
}
