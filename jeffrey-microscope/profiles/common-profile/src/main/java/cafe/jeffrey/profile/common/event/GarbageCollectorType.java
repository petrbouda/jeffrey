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

public enum GarbageCollectorType {
    SERIAL(false, "DefNew", "SerialOld", "UseSerialGC"),
    PARALLEL(false, "ParallelScavenge", "ParallelOld", "UseParallelGC"),
    G1(true, "G1New", "G1Old", "UseG1GC"),
    Z(true, null, "Z", "UseZGC"),
    ZGENERATIONAL(true, "ZGC Minor", "ZGC Major", "UseZGC"),
    SHENANDOAH(true, null, "Shenandoah", "UseShenandoahGC"),
    // Has the same new and old generation collector as a SerialGC
    // Disable for the same of simplicity at this time
    // EPSILON("DefNew", "SerialOld", "UseEpsilonGC")
    ;

    private static final GarbageCollectorType[] VALUES = values();

    private final boolean isConcurrent;
    private final String youngGenCollector;
    private final String oldGenCollector;
    private final String jvmFlagName;

    GarbageCollectorType(boolean isConcurrent, String youngGenCollector, String oldGenCollector, String jvmFlagName) {
        this.isConcurrent = isConcurrent;
        this.youngGenCollector = youngGenCollector;
        this.oldGenCollector = oldGenCollector;
        this.jvmFlagName = jvmFlagName;
    }

    public static GarbageCollectorType fromOldGenCollector(String oldGenCollector) {
        for (GarbageCollectorType type : VALUES) {
            if (type.oldGenCollector.equalsIgnoreCase(oldGenCollector)) {
                return type;
            }
        }
        return null;
    }

    public String getYoungGenCollector() {
        return youngGenCollector;
    }

    public String getOldGenCollector() {
        return oldGenCollector;
    }

    public boolean isConcurrent() {
        return isConcurrent;
    }


    /**
     * Checks if the given flag name is a GC-related JVM flag.
     */
    public static boolean isGcFlag(String flagName) {
        for (GarbageCollectorType type : VALUES) {
            if (type.jvmFlagName.equals(flagName)) {
                return true;
            }
        }
        return false;
    }
}
