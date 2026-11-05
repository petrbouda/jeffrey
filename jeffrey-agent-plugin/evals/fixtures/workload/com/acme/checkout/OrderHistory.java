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

package com.acme.checkout;

import cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent;
import cafe.jeffrey.jfr.events.trace.EventAttributes;
import cafe.jeffrey.jfr.events.trace.TracedEvents;
import cafe.jeffrey.jfr.events.trace.Tracer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * The second workload behind the microscope plugin's eval fixtures: the checkout service's order
 * history, with the statement shape the code tools are asked about.
 * <ul>
 *   <li>every {@code GET /orders/{customer}} is a traced exchange (jeffrey-events, jeffrey-tracing);</li>
 *   <li>one {@code orders.by-customer} query finds the customer's orders, then
 *       {@code OrderRepository.findLines} runs one {@code order-lines.by-order} query <em>per order</em>
 *       — an N+1 a single query over all the order ids would replace;</li>
 *   <li>each statement goes through the service's own {@code Database.query}, which records it as a
 *       {@code jeffrey.JdbcQuery} event with its stack, the way a JDBC wrapper would; the database's
 *       round trip is a park, so no driver or database is needed.</li>
 * </ul>
 * Run as a single source file with the tracing jars on the class path:
 * {@code java -cp jeffrey-events.jar:jeffrey-tracing-api.jar:jeffrey-tracing-thread-local.jar OrderHistory.java
 * <seconds>}. {@code build/scripts/record-plugin-eval-mocks.py} records it under JFR and imports it into Jeffrey.
 */
public final class OrderHistory {

    private static final int WORKERS = 2;
    private static final int CUSTOMERS = 200;
    private static final int MIN_ORDERS = 6;
    private static final int MAX_ORDERS = 16;
    private static final Duration THINK_TIME = Duration.ofMillis(20);
    private static final String METHOD = "GET";
    private static final String URI = "/orders/{customer}";
    private static final String MEDIA_TYPE = "application/json";
    private static final int OK = 200;

    public static void main(String[] args) throws Exception {
        Duration duration = Duration.ofSeconds(Long.parseLong(args[0]));
        AtomicBoolean running = new AtomicBoolean(true);
        OrderController controller = new OrderController(new OrderService(new OrderRepository(new Database())));
        ExecutorService workers = Executors.newFixedThreadPool(WORKERS, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("orders-worker-" + thread.threadId());
            return thread;
        });
        for (int i = 0; i < WORKERS; i++) {
            workers.submit(() -> {
                while (running.get()) {
                    String customer = "customer-" + ThreadLocalRandom.current().nextInt(CUSTOMERS);
                    traced(customer, () -> controller.history(customer));
                    LockSupport.parkNanos(THINK_TIME.toNanos());
                }
            });
        }
        Thread.sleep(duration.toMillis());
        running.set(false);
        workers.shutdown();
        workers.awaitTermination(10, TimeUnit.SECONDS);
    }

    /** The request as an inbound HTTP exchange: the trace's root span, the statements under it. */
    private static void traced(String customer, Runnable body) {
        HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();
        exchange.begin();
        try {
            Tracer.inSpanOf(exchange, body);
        } finally {
            exchange.end();
            if (exchange.shouldCommit()) {
                exchange.method = METHOD;
                exchange.uri = URI;
                exchange.mediaType = MEDIA_TYPE;
                exchange.statusCode = OK;
                exchange.attributes = EventAttributes.create()
                        .put("customer.segment", customer.hashCode() % 2 == 0 ? "retail" : "business")
                        .json();
                exchange.commitSpan();
            }
        }
    }

    record OrderSummary(long orderId, int lines, long totalCents) {
    }

    record Line(long orderId, long cents) {
    }

    static final class OrderController {

        private final OrderService service;

        OrderController(OrderService service) {
            this.service = service;
        }

        List<OrderSummary> history(String customer) {
            return service.history(customer);
        }
    }

    static final class OrderService {

        private final OrderRepository repository;

        OrderService(OrderRepository repository) {
            this.repository = repository;
        }

        List<OrderSummary> history(String customer) {
            List<Long> orderIds = repository.findOrders(customer);
            List<OrderSummary> summaries = new ArrayList<>(orderIds.size());
            for (long orderId : orderIds) {
                List<Line> lines = repository.findLines(orderId);
                summaries.add(summarize(orderId, lines));
            }
            return summaries;
        }

        private static OrderSummary summarize(long orderId, List<Line> lines) {
            long total = 0;
            for (Line line : lines) {
                long cents = line.cents();
                for (int i = 0; i < 20_000; i++) {
                    cents = (cents * 31 + i) % 1_000_003;
                }
                total += cents;
            }
            return new OrderSummary(orderId, lines.size(), total);
        }
    }

    static final class OrderRepository {

        private static final String ORDERS_BY_CUSTOMER = "orders.by-customer";
        private static final String LINES_BY_ORDER = "order-lines.by-order";
        private static final String ORDERS_SQL = "SELECT id FROM orders WHERE customer = ?";
        private static final String LINES_SQL = "SELECT order_id, cents FROM order_lines WHERE order_id = ?";

        private final Database database;

        OrderRepository(Database database) {
            this.database = database;
        }

        List<Long> findOrders(String customer) {
            int count = ThreadLocalRandom.current().nextInt(MIN_ORDERS, MAX_ORDERS + 1);
            database.query(ORDERS_BY_CUSTOMER, ORDERS_SQL, count);
            List<Long> ids = new ArrayList<>(count);
            long base = (long) customer.hashCode() << 8;
            for (int i = 0; i < count; i++) {
                ids.add(base + i);
            }
            return ids;
        }

        List<Line> findLines(long orderId) {
            int count = ThreadLocalRandom.current().nextInt(1, 5);
            database.query(LINES_BY_ORDER, LINES_SQL, count);
            List<Line> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(new Line(orderId, ThreadLocalRandom.current().nextLong(100, 100_000)));
            }
            return lines;
        }
    }

    /** The service's own JDBC wrapper: every statement is recorded with the stack of the code that ran it. */
    static final class Database {

        private static final String GROUP = "orders";
        private static final long ROUND_TRIP_NANOS = Duration.ofMillis(1).toNanos();

        void query(String name, String sql, int rows) {
            TracedEvents.emit(new JdbcQueryEvent(name, GROUP), () -> {
                LockSupport.parkNanos(ROUND_TRIP_NANOS);
                return rows;
            }, (event, returned) -> {
                event.sql = sql;
                event.rows = returned == null ? 0 : returned;
            });
        }
    }
}
