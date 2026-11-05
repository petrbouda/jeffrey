#!/usr/bin/env bash
# The profiled application's repository with the history the live recordings were imported at: commit A
# prices a checkout without formatting its lines (the baseline run, line for line), commit B adds the formatLine call to
# PriceCalculator.reprice (the candidate run). Author, committer and dates are fixed, so A and B hash the
# same everywhere: they are `liveCommits` in fixtures/recorded-profiles.json, which --load imports the two
# runs at (test-plugin-eval-mocks.py checks the hashes, and the files between the markers verbatim).
set -euo pipefail
export GIT_AUTHOR_NAME=eval GIT_AUTHOR_EMAIL=eval@example.com GIT_COMMITTER_NAME=eval GIT_COMMITTER_EMAIL=eval@example.com
commit() {
  GIT_AUTHOR_DATE="$1" GIT_COMMITTER_DATE="$1" git -c commit.gpgsign=false commit -q -m "$2"
}
mkdir -p src/main/java/com/acme/checkout
cat > src/main/java/com/acme/checkout/Checkout.java <<'CHECKOUT_JAVA'
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
import cafe.jeffrey.jfr.events.trace.EventAttributes;
import cafe.jeffrey.jfr.events.trace.Tracer;
import com.sun.management.HotSpotDiagnosticMXBean;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * The workload behind the microscope plugin's eval fixtures: a checkout service in miniature, with
 * one of each thing the skills are asked about, so a recording of it holds an answer to each.
 * <ul>
 *   <li>CPU in {@code PriceCalculator.reprice} — and, in the {@code candidate} mode, a regression in
 *       {@code PriceCalculator.formatLine} that the compare case is expected to find;</li>
 *   <li>allocation of a {@code HashMap} per line item;</li>
 *   <li>contention on the {@code SessionCache} monitor;</li>
 *   <li>exceptions as control flow — {@code NumberFormatException} from {@code CouponParser};</li>
 *   <li>a slow leak: {@code AuditLog} keeps every order it is given;</li>
 *   <li>traces: one checkout in {@value #TRACE_EVERY} is a {@code POST /checkout} exchange with a span per
 *       step under it ({@code jeffrey-events} and {@code jeffrey-tracing}), sampled as a service would
 *       be, so the slow ones are the requests that waited on {@code SessionCache}.</li>
 * </ul>
 * Run as a single source file with the tracing jars on the class path:
 * {@code java -cp jeffrey-events.jar:jeffrey-tracing-api.jar:jeffrey-tracing-thread-local.jar Checkout.java
 * <baseline|candidate> <seconds> [heap-dump-path]}.
 * {@code build/scripts/record-plugin-eval-mocks.py} records it under JFR and imports it into Jeffrey.
 */
public final class Checkout {

    private static final int WORKERS = 4;
    private static final int LINES_PER_ORDER = 24;
    private static final String CANDIDATE = "candidate";
    private static final int TRACE_EVERY = 50;
    private static final String METHOD = "POST";
    private static final String URI = "/checkout";
    private static final String MEDIA_TYPE = "application/json";
    private static final int OK = 200;
    private static final String PROMO_PREFIX = "SPRING-";

    public static void main(String[] args) throws Exception {
        boolean candidate = CANDIDATE.equals(args[0]);
        Duration duration = Duration.ofSeconds(Long.parseLong(args[1]));
        AtomicBoolean running = new AtomicBoolean(true);
        SessionCache cache = new SessionCache();
        AuditLog audit = new AuditLog();
        ExecutorService workers = Executors.newFixedThreadPool(WORKERS, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("checkout-worker-" + thread.threadId());
            return thread;
        });
        for (int i = 0; i < WORKERS; i++) {
            workers.submit(() -> {
                PriceCalculator calculator = new PriceCalculator(candidate);
                long handled = 0;
                while (running.get()) {
                    Order order = Order.random();
                    if (++handled % TRACE_EVERY == 0) {
                        traced(order, () -> checkout(order, calculator, cache, audit, Checkout::span));
                    } else {
                        checkout(order, calculator, cache, audit, Checkout::untraced);
                    }
                }
            });
        }
        Thread.sleep(duration.toMillis());
        running.set(false);
        workers.shutdown();
        workers.awaitTermination(10, TimeUnit.SECONDS);
        if (args.length > 2) {
            ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).dumpHeap(args[2], true);
        }
        System.out.println("orders audited: " + audit.size());
    }

    /** The steps of one checkout, each run through {@code step} — a span when the request is traced. */
    private static void checkout(Order order, PriceCalculator calculator, SessionCache cache, AuditLog audit,
                                 Step step) {
        step.run("price.reprice", () -> calculator.reprice(order));
        step.run("session.touch", () -> {
            cache.touch(order.customer());
            return null;
        });
        step.run("coupon.discount", () -> CouponParser.discount(order.coupon()));
        step.run("audit.record", () -> {
            audit.record(order);
            return null;
        });
    }

    /** The request as an inbound HTTP exchange: the trace's root span, the steps nested under it. */
    private static void traced(Order order, Runnable body) {
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
                        .put("coupon", order.coupon().startsWith(PROMO_PREFIX) ? "promo" : "numeric")
                        .put("lines", order.lineCents().size())
                        .json();
                exchange.commitSpan();
            }
        }
    }

    private static <R> R span(String name, Supplier<R> body) {
        return Tracer.call(name, body);
    }

    private static <R> R untraced(String name, Supplier<R> body) {
        return body.get();
    }

    @FunctionalInterface
    interface Step {
        <R> R run(String name, Supplier<R> body);
    }

    record Order(String customer, String coupon, List<Long> lineCents) {

        static Order random() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            List<Long> lines = new ArrayList<>(LINES_PER_ORDER);
            for (int i = 0; i < LINES_PER_ORDER; i++) {
                lines.add(random.nextLong(100, 100_000));
            }
            String coupon = random.nextInt(4) == 0 ? PROMO_PREFIX + random.nextInt(100) : Integer.toString(random.nextInt(50));
            return new Order("customer-" + random.nextInt(200), coupon, lines);
        }
    }

    static final class PriceCalculator {

        private final boolean candidate;

        PriceCalculator(boolean candidate) {
            this.candidate = candidate;
        }

        long reprice(Order order) {
            long total = 0;
            for (long cents : order.lineCents()) {
                Map<String, Long> line = new HashMap<>();
                line.put("net", cents);
                line.put("tax", taxOf(cents));
                total += line.get("net") + line.get("tax");
                if (candidate) {
                    total += formatLine(line).length();
                }
            }
            return total;
        }

        private static long taxOf(long cents) {
            double tax = cents;
            for (int i = 0; i < 400; i++) {
                tax = Math.sqrt(tax * 1.21 + i);
            }
            return (long) tax;
        }

        private static String formatLine(Map<String, Long> line) {
            String text = "";
            for (int i = 0; i < 6; i++) {
                text = String.format("%s|net=%,d|tax=%,d", text, line.get("net"), line.get("tax"));
            }
            return text;
        }
    }

    static final class SessionCache {

        private final Map<String, Long> lastSeen = new HashMap<>();

        synchronized void touch(String customer) {
            lastSeen.merge(customer, 1L, Long::sum);
            if (lastSeen.get(customer) % 50 == 0) {
                busyWait(Duration.ofMillis(12));
            }
        }

        private static void busyWait(Duration duration) {
            long until = System.nanoTime() + duration.toNanos();
            while (System.nanoTime() < until) {
                Thread.onSpinWait();
            }
        }
    }

    static final class CouponParser {

        static int discount(String coupon) {
            try {
                return Integer.parseInt(coupon);
            } catch (NumberFormatException notANumber) {
                return 0;
            }
        }
    }

    static final class AuditLog {

        private final List<Order> orders = new ArrayList<>();

        synchronized void record(Order order) {
            if (ThreadLocalRandom.current().nextInt(20) == 0) {
                orders.add(order);
            }
        }

        synchronized int size() {
            return orders.size();
        }
    }
}
CHECKOUT_JAVA
cat > src/main/java/com/acme/checkout/OrderHistory.java <<'ORDER_HISTORY_JAVA'
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
ORDER_HISTORY_JAVA
git -c init.defaultBranch=main init -q
# Commit A: the same source without the per-line formatting in reprice. The three lines the call takes in
# B are comments here, so every other line is where it is in B and the baseline's frames name A's lines.
cp src/main/java/com/acme/checkout/Checkout.java .checkout-b.java
awk '
    /^                if \(candidate\) \{$/ {
        print "                // Receipt lines are priced but not formatted yet:"
        print "                // formatLine is written, and joins the loop once the"
        print "                // receipt layout is settled."
        skip = 2
        next
    }
    skip > 0 { skip--; next }
    { print }
' .checkout-b.java > src/main/java/com/acme/checkout/Checkout.java
git add src
commit "2026-10-20T09:00:00+00:00" "Price each checkout line"
# Commit B: the source both runs were recorded from.
mv .checkout-b.java src/main/java/com/acme/checkout/Checkout.java
git add src
commit "2026-10-21T09:00:00+00:00" "Format every priced line for the receipt"
