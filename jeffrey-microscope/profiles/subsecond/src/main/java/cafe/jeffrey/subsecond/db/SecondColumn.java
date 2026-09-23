/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
