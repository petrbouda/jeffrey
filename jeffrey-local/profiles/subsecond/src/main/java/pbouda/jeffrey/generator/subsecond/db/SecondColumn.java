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

package pbouda.jeffrey.generator.subsecond.db;

public final class SecondColumn {

    private static final int MILLIS = 1000;

    public static final int BUCKET_SIZE = 20;
    public static final int BUCKET_COUNT = MILLIS / BUCKET_SIZE;

    private final long[] buckets;
    private long maxValue;

    public SecondColumn() {
        this.buckets = new long[BUCKET_COUNT];
    }

    public long increment(int i, long value) {
        int bucket = i / BUCKET_SIZE;
        long newValue = buckets[bucket] + value;
        buckets[bucket] = newValue;
        maxValue = Math.max(maxValue, newValue);
        return newValue;
    }

    public long[] getBuckets() {
        return buckets;
    }
}
