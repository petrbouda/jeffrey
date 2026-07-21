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

package cafe.jeffrey.subsecond.db;

public final class SecondColumn {

    private static final int MILLIS = 1000;

    /** Default sub-second bucket width (ms) and row count, kept for back-compatible callers. */
    public static final int BUCKET_SIZE = 20;
    public static final int BUCKET_COUNT = MILLIS / BUCKET_SIZE;

    private final int bucketSize;
    private final int bucketCount;
    private final long[] buckets;
    private long maxValue;

    public SecondColumn() {
        this(BUCKET_SIZE);
    }

    public SecondColumn(int bucketSize) {
        this.bucketSize = bucketSize;
        this.bucketCount = MILLIS / bucketSize;
        this.buckets = new long[bucketCount];
    }

    public long increment(int i, long value) {
        int bucket = i / bucketSize;
        long newValue = buckets[bucket] + value;
        buckets[bucket] = newValue;
        maxValue = Math.max(maxValue, newValue);
        return newValue;
    }

    public long[] getBuckets() {
        return buckets;
    }

    public int getBucketSize() {
        return bucketSize;
    }

    public int getBucketCount() {
        return bucketCount;
    }
}
