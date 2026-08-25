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
