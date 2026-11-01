#!/usr/bin/env bash
# The profiled application's repository: the workload the recording came from, committed, so the
# working directory holds the code a finding names. The file between the markers is
# fixtures/workload/com/acme/checkout/Checkout.java verbatim (test-plugin-eval-mocks.py checks it).
set -euo pipefail
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

/**
 * The workload behind the microscope plugin's eval fixtures: a checkout service in miniature, with
 * one of each thing the skills are asked about, so a recording of it holds an answer to each.
 * <ul>
 *   <li>CPU in {@code PriceCalculator.reprice} — and, in the {@code candidate} mode, a regression in
 *       {@code PriceCalculator.formatLine} that the compare case is expected to find;</li>
 *   <li>allocation of a {@code HashMap} per line item;</li>
 *   <li>contention on the {@code SessionCache} monitor;</li>
 *   <li>exceptions as control flow — {@code NumberFormatException} from {@code CouponParser};</li>
 *   <li>a slow leak: {@code AuditLog} keeps every order it is given.</li>
 * </ul>
 * Run as a single source file: {@code java Checkout.java <baseline|candidate> <seconds> [heap-dump-path]}.
 * {@code build/scripts/record-plugin-eval-mocks.py} records it under JFR and imports it into Jeffrey.
 */
public final class Checkout {

    private static final int WORKERS = 4;
    private static final int LINES_PER_ORDER = 24;
    private static final String CANDIDATE = "candidate";

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
                while (running.get()) {
                    Order order = Order.random();
                    calculator.reprice(order);
                    cache.touch(order.customer());
                    CouponParser.discount(order.coupon());
                    audit.record(order);
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

    record Order(String customer, String coupon, List<Long> lineCents) {

        static Order random() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            List<Long> lines = new ArrayList<>(LINES_PER_ORDER);
            for (int i = 0; i < LINES_PER_ORDER; i++) {
                lines.add(random.nextLong(100, 100_000));
            }
            String coupon = random.nextInt(4) == 0 ? "SPRING-" + random.nextInt(100) : Integer.toString(random.nextInt(50));
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
git init -q
git add -A
git -c user.name=eval -c user.email=eval@example.com commit -q -m "checkout service"
