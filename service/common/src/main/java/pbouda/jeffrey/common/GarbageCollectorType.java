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

public enum GarbageCollectorType {
    SERIAL(false, "DefNew", "SerialOld"),
    PARALLEL(false, "ParallelScavenge", "ParallelOld"),
    G1(true, "G1New", "G1Old"),
    Z(true, null, "Z"),
    ZGENERATIONAL(true, "ZGC Minor", "ZGC Major"),
    SHENANDOAH(true, null, "Shenandoah"),
    // Has the same new and old generation collector as a SerialGC
    // Disable for the same of simplicity at this time
    // EPSILON("DefNew", "SerialOld")
    ;

    private static final GarbageCollectorType[] VALUES = values();

    private final boolean isConcurrent;
    private final String youngGenCollector;
    private final String oldGenCollector;

    GarbageCollectorType(boolean isConcurrent, String youngGenCollector, String oldGenCollector) {
        this.isConcurrent = isConcurrent;
        this.youngGenCollector = youngGenCollector;
        this.oldGenCollector = oldGenCollector;
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
}
