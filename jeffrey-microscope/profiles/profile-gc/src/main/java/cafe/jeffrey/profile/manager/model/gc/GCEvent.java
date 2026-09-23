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

package cafe.jeffrey.profile.manager.model.gc;

import java.math.BigDecimal;

public class GCEvent {

    private final long timestamp;
    private final long gcId;
    private final GCGenerationType generationType;
    private final String collectorName;
    private final String cause;
    private final long duration;
    private final long beforeGC;
    private final long afterGC;
    private final long freed;
    private final BigDecimal efficiency;
    private final long heapSize;
    private final long sumOfPauses;
    private final long longestPause;
    private final boolean concurrent;

    private String type;

    public GCEvent(
            long timestamp,
            long gcId,
            GCGenerationType generationType,
            String collectorName,
            String cause,
            long duration,
            long beforeGC,
            long afterGC,
            long freed,
            BigDecimal efficiency,
            long heapSize,
            long sumOfPauses,
            long longestPause,
            boolean concurrent) {

        this.timestamp = timestamp;
        this.gcId = gcId;
        this.generationType = generationType;
        this.collectorName = collectorName;
        this.cause = cause;
        this.duration = duration;
        this.beforeGC = beforeGC;
        this.afterGC = afterGC;
        this.freed = freed;
        this.efficiency = efficiency;
        this.heapSize = heapSize;
        this.sumOfPauses = sumOfPauses;
        this.longestPause = longestPause;
        this.concurrent = concurrent;
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

    public String getCollectorName() {
        return collectorName;
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

    public boolean isConcurrent() {
        return concurrent;
    }
}
