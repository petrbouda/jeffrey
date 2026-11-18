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

package cafe.jeffrey.microscope.profile.management.manager.custom.builder;

import org.HdrHistogram.Histogram;

/**
 * The latency distribution of a set of exchanges — HTTP requests, gRPC calls, JDBC statements — and the
 * figures every dashboard shows of it. An empty distribution answers 0 for every figure.
 * <p>
 * A whole dashboard's distribution is the {@linkplain #add merge} of its parts': a maximum of per-part
 * percentiles is not a percentile of the whole.
 */
final class LatencyStats {

    private static final int SIGNIFICANT_DIGITS = 3;
    private static final double P99 = 99.0;
    private static final double P95 = 95.0;

    private final Histogram histogram = new Histogram(SIGNIFICANT_DIGITS);

    void record(long latency) {
        histogram.recordValue(latency);
    }

    void add(LatencyStats other) {
        histogram.add(other.histogram);
    }

    long count() {
        return histogram.getTotalCount();
    }

    /** The largest latency recorded, to the histogram's precision. */
    long max() {
        return histogram.getMaxValue();
    }

    long p99() {
        return histogram.getValueAtPercentile(P99);
    }

    long p95() {
        return histogram.getValueAtPercentile(P95);
    }
}
