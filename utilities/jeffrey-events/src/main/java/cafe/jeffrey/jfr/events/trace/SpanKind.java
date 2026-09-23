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
