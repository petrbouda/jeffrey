/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.manager.builder;

import pbouda.jeffrey.manager.model.gc.GCPauseBucket;
import pbouda.jeffrey.manager.model.gc.GCPauseDistribution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SimpleTimeDistributionCollector {

    private static final BigDecimal ZERO_SCALED = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final long[] values;
    private final int[] buckets;
    private final String[] labels;

    public SimpleTimeDistributionCollector(int[] buckets, String[] labels) {
        Objects.requireNonNull(buckets, "Values cannot be null");
        Objects.requireNonNull(labels, "Labels cannot be null");
        if (buckets.length != labels.length) {
            throw new IllegalArgumentException("Values and labels must have the same length");
        }

        this.buckets = buckets;
        this.labels = labels;
        this.values = new long[buckets.length];
    }

    public void record(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value cannot be negative");
        }

        for (int i = 0; i < buckets.length; i++) {
            if (value <= buckets[i]) {
                values[i]++;
                return;
            }
        }

        // If value exceeds all defined buckets, it is recorded in the last bucket
        values[buckets.length - 1]++;
    }

    public GCPauseDistribution build() {
        List<GCPauseBucket> bucketList = new ArrayList<>();
        long total = Arrays.stream(values).sum();

        for (int i = 0; i < buckets.length; i++) {
            BigDecimal percentage = total > 0 ? bigDecimal((double) values[i] / total * 100) : ZERO_SCALED;
            bucketList.add(new GCPauseBucket(labels[i], values[i], percentage));
        }

        return new GCPauseDistribution(bucketList);
    }

    private static BigDecimal bigDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
