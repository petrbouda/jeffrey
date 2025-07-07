/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.common;

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
