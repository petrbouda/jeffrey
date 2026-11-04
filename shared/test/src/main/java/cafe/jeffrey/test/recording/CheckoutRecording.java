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

package cafe.jeffrey.test.recording;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

/**
 * Two recordings of a checkout service in miniature that traces its requests, kept in
 * <a href="https://github.com/petrbouda/jeffrey-recordings">jeffrey-recordings</a>
 * ({@code plugin-evals/}, commit {@code 3017ffe}) and copied byte for byte beside this class.
 *
 * <p>The workload is the microscope plugin's eval workload
 * ({@code jeffrey-claude-plugin/evals/fixtures/workload/}), run for 30 s on JDK 25 under the JDK's own
 * recorder ({@code settings=profile}, every exception throw) with jeffrey-events and jeffrey-tracing on
 * its class path: four workers check orders out, and one checkout in {@value #TRACED_EVERY} is a traced
 * {@code POST /checkout} — a {@code jeffrey.HttpServerExchange} as the trace's root, with a
 * {@code jeffrey.TraceSpan} under it for each of the {@link #STEPS}, all on the worker's thread. The
 * root carries two attributes: {@code coupon} ({@code promo} or {@code numeric}) and {@code lines}
 * (always 24). The slow requests are the ones that waited on the session cache's monitor; the
 * coupon parser throws a {@code NumberFormatException} for every promo coupon. The candidate run adds
 * a regression: {@code PriceCalculator.formatLine}, inside {@code price.reprice}.
 *
 * <p>Each file is one chunk. Every figure below is what {@code jfr summary} and {@code jfr print}
 * read from it.
 */
public enum CheckoutRecording {

    BASELINE("checkout-baseline.jfr",
            Instant.parse("2026-11-01T14:29:52.435810681Z"), Instant.parse("2026-11-01T14:30:22.888484856Z"),
            new Counts(2_381, 9_524, 3_072, 1_510, 6_023, 29_907),
            new Traces(590, 1_791, 24_999_387L)),
    CANDIDATE("checkout-candidate.jfr",
            Instant.parse("2026-11-01T14:30:23.174889208Z"), Instant.parse("2026-11-01T14:30:53.717511802Z"),
            new Counts(2_281, 9_124, 3_182, 8_483, 6_058, 28_425),
            new Traces(536, 1_745, 28_186_390L));

    public static final String HTTP_SERVER_EXCHANGE = "jeffrey.HttpServerExchange";
    public static final String TRACE_SPAN = "jeffrey.TraceSpan";
    public static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    public static final String ALLOCATION_SAMPLE = "jdk.ObjectAllocationSample";
    public static final String MONITOR_ENTER = "jdk.JavaMonitorEnter";
    public static final String EXCEPTION_THROW = "jdk.JavaExceptionThrow";

    /** The traced request: its operation, kind, method, URI and status. */
    public static final String OPERATION = "POST /checkout";
    public static final String OPERATION_KIND = "SERVER";
    public static final String METHOD = "POST";
    public static final String URI = "/checkout";
    public static final int STATUS_CODE = 200;

    /** The steps of a checkout, each a span under the request's root, in the order they run. */
    public static final List<String> STEPS = List.of("price.reprice", "session.touch", "coupon.discount", "audit.record");

    /** One checkout in this many is traced. */
    public static final int TRACED_EVERY = 50;

    /** The root's attribute naming the kind of coupon, and its two values. */
    public static final String COUPON = "coupon";
    public static final String PROMO = "promo";
    public static final String NUMERIC = "numeric";
    /** The root's attribute counting the order's lines; every order has {@value #LINES_PER_ORDER}. */
    public static final String LINES = "lines";
    public static final int LINES_PER_ORDER = 24;

    private static final String RESOURCE_DIRECTORY = "checkout/";

    /** The heap dump taken at the end of the candidate run, gzipped, as jeffrey-recordings keeps it. */
    public static final String HEAP_DUMP_FILE = "checkout.hprof.gz";

    /**
     * What the dump holds, read with an independent HPROF reader: the orders, those whose coupon
     * starts with {@code SPRING-}, and every instance, object array, primitive array and class dumped.
     */
    public static final HeapFigures HEAP_FIGURES = new HeapFigures(
            "com.acme.checkout.Checkout$Order", 5_822, 1_441, 254_545, 15_962, 45_580, 4_209);

    private final String resource;
    private final Instant recordingStartedAt;
    private final Instant recordingFinishedAt;
    private final Counts counts;
    private final Traces traces;

    CheckoutRecording(String resource, Instant recordingStartedAt, Instant recordingFinishedAt,
                      Counts counts, Traces traces) {
        this.resource = resource;
        this.recordingStartedAt = recordingStartedAt;
        this.recordingFinishedAt = recordingFinishedAt;
        this.counts = counts;
        this.traces = traces;
    }

    /** The file's name, as kept in jeffrey-recordings. */
    public String fileName() {
        return resource;
    }

    /** When its one chunk began, to the nanosecond, as its header states. */
    public Instant recordingStartedAt() {
        return recordingStartedAt;
    }

    /** When its one chunk ended: its start and its duration, as its header states. */
    public Instant recordingFinishedAt() {
        return recordingFinishedAt;
    }

    /** What the recording holds of the event types the tests read. */
    public Counts counts() {
        return counts;
    }

    /** What its traces hold. */
    public Traces traces() {
        return traces;
    }

    /** The file, as kept in jeffrey-recordings. */
    public byte[] bytes() {
        return resource(resource);
    }

    /** The heap dump taken at the end of the candidate run, gzipped. */
    public static byte[] heapDump() {
        return resource(HEAP_DUMP_FILE);
    }

    private static byte[] resource(String name) {
        try (InputStream in = CheckoutRecording.class.getResourceAsStream(RESOURCE_DIRECTORY + name)) {
            if (in == null) {
                throw new IllegalStateException("A recording is missing from the classpath: " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * What a recording holds, by event type.
     *
     * @param exchanges         {@code jeffrey.HttpServerExchange}: one per traced request, its root
     * @param spans             {@code jeffrey.TraceSpan}: the steps, four per traced request
     * @param executionSamples  {@code jdk.ExecutionSample}
     * @param allocationSamples {@code jdk.ObjectAllocationSample}
     * @param monitorEnters     {@code jdk.JavaMonitorEnter}
     * @param exceptionThrows   {@code jdk.JavaExceptionThrow}
     */
    public record Counts(int exchanges, int spans, int executionSamples, int allocationSamples,
                         int monitorEnters, int exceptionThrows) {
    }

    /**
     * What the recording's traces hold, read from the events themselves: every span's parent is its
     * request's root, every root has the four steps under it, nothing failed and every request answered
     * {@value #STATUS_CODE}.
     *
     * @param promo        requests whose {@code coupon} attribute is {@code promo}
     * @param numeric      requests whose {@code coupon} attribute is {@code numeric}
     * @param slowestNanos the longest request, root to end
     */
    public record Traces(int promo, int numeric, long slowestNanos) {
    }

    /**
     * What the heap dump holds.
     *
     * @param orderClass      the workload's order type, by its Java name
     * @param orders          its instances: every order the audit log kept, and those in flight
     * @param promoOrders     the orders whose coupon starts with {@code SPRING-}
     * @param instances       every instance dumped
     * @param objectArrays    every object array dumped
     * @param primitiveArrays every primitive array dumped
     * @param classes         every class dumped
     */
    public record HeapFigures(String orderClass, int orders, int promoOrders, int instances, int objectArrays,
                              int primitiveArrays, int classes) {
    }
}
