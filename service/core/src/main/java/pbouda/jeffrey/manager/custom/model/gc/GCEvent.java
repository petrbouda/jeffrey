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

package pbouda.jeffrey.manager.custom.model.gc;

import java.math.BigDecimal;

public class GCEvent {

    private final long timestamp;
    private final long gcId;
    private final GCGenerationType generationType;
    private final String generation;
    private final String cause;
    private final long duration;
    private final long beforeGC;
    private final long afterGC;
    private final long freed;
    private final BigDecimal efficiency;
    private final long heapSize;
    private final long sumOfPauses;
    private final long longestPause;

    private String type;

    public GCEvent(
            long timestamp,
            long gcId,
            GCGenerationType generationType,
            String generation,
            String cause,
            long duration,
            long beforeGC,
            long afterGC,
            long freed,
            BigDecimal efficiency,
            long heapSize,
            long sumOfPauses,
            long longestPause) {

        this.timestamp = timestamp;
        this.gcId = gcId;
        this.generationType = generationType;
        this.generation = generation;
        this.cause = cause;
        this.duration = duration;
        this.beforeGC = beforeGC;
        this.afterGC = afterGC;
        this.freed = freed;
        this.efficiency = efficiency;
        this.heapSize = heapSize;
        this.sumOfPauses = sumOfPauses;
        this.longestPause = longestPause;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getGcId() {
        return gcId;
    }

    public String getGeneration() {
        return generation;
    }

    public String getType() {
        return type;
    }

    public String getCause() {
        return cause;
    }

    public long getDuration() {
        return duration;
    }

    public long getBeforeGC() {
        return beforeGC;
    }

    public long getAfterGC() {
        return afterGC;
    }

    public long getFreed() {
        return freed;
    }

    public BigDecimal getEfficiency() {
        return efficiency;
    }

    public long getHeapSize() {
        return heapSize;
    }

    public long getSumOfPauses() {
        return sumOfPauses;
    }

    public long getLongestPause() {
        return longestPause;
    }

    public GCGenerationType getGenerationType() {
        return generationType;
    }
}
