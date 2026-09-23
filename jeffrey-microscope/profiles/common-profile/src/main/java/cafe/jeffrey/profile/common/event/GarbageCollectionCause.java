/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.common.event;

public enum GarbageCollectionCause {
    SYSTEM_GC("System.gc()"),
    ALLOCATION_FAILURE("Allocation Failure"),
    METADATA_GC_THRESHOLD("Metadata GC Threshold"),
    ERGONOMICS("Ergonomics"),
    G1_EVACUATION_PAUSE("G1 Evacuation Pause"),
    G1_HUMONGOUS_ALLOCATION("G1 Humongous Allocation"),
    LAST_DITCH_COLLECTION("Last Ditch Collection"),
    CONCURRENT_MARK_START("Concurrent Mark Start"),
    CONCURRENT_MODE_FAILURE("Concurrent Mode Failure"),
    PROMOTION_FAILED("Promotion Failed"),
    TO_SPACE_EXHAUSTED("To-space Exhausted"),
    GCLOCKER_INITIATED_GC("GCLocker Initiated GC"),
    HEAP_INSPECTION_DUMP("Heap Inspection/Dump"),
    WARMUP("Warmup"),
    TIMER("Timer"),
    DIAGNOSTIC_COMMAND("Diagnostic Command"),
    JFR_PERIODIC("JFR Periodic"),
    PROACTIVE("Proactive"),
    METADATA_GC_CLEAR_SOFT_REFERENCES("Metadata GC Clear Soft References");

    private final String description;

    GarbageCollectionCause(String description) {
        this.description = description;
    }

    public boolean sameAs(String description) {
        return this.description.equalsIgnoreCase(description);
    }

    public String getDescription() {
        return description;
    }
}
