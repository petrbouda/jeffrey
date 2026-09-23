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

package cafe.jeffrey.jfr.events;

import cafe.jeffrey.jfr.events.grpc.GrpcClientExchangeEvent;
import cafe.jeffrey.jfr.events.grpc.GrpcServerExchangeEvent;
import cafe.jeffrey.jfr.events.http.HttpClientExchangeEvent;
import cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent;
import cafe.jeffrey.jfr.events.jdbc.pool.*;
import cafe.jeffrey.jfr.events.jdbc.statement.*;
import cafe.jeffrey.jfr.events.notification.NotificationEvent;
import cafe.jeffrey.jfr.events.trace.TraceSpanEvent;
import jdk.jfr.Event;

import java.util.List;

/**
 * Every event type this library ships, in one list — for tooling that needs the catalog: eager
 * {@link jdk.jfr.FlightRecorder#register registration}, settings generation, documentation.
 * <p>
 * Ordinary instrumentation never needs it: JFR auto-registers an event type the first time an
 * instance of its class is created, so committed events always land in the recording with full
 * metadata.
 */
public abstract class JeffreyEventRegistry {

    private static final List<Class<? extends Event>> EVENTS = List.of(
            GrpcClientExchangeEvent.class,
            GrpcServerExchangeEvent.class,
            HttpClientExchangeEvent.class,
            HttpServerExchangeEvent.class,
            NotificationEvent.class,
            JdbcExecuteEvent.class,
            JdbcInsertEvent.class,
            JdbcDeleteEvent.class,
            JdbcUpdateEvent.class,
            JdbcQueryEvent.class,
            JdbcStreamEvent.class,
            PooledJdbcConnectionAcquiredEvent.class,
            PooledJdbcConnectionBorrowedEvent.class,
            PooledJdbcConnectionCreatedEvent.class,
            AcquiringPooledJdbcConnectionTimeoutEvent.class,
            JdbcPoolStatisticsEvent.class,
            TraceSpanEvent.class
    );

    private JeffreyEventRegistry() {
    }

    public static List<Class<? extends Event>> all() {
        return EVENTS;
    }
}
