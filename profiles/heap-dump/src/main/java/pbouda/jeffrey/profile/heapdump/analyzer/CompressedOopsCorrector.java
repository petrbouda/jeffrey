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

package pbouda.jeffrey.profile.heapdump.analyzer;

import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.ObjectArrayInstance;
import org.netbeans.lib.profiler.heap.ObjectFieldValue;

import java.util.List;

/**
 * Shared utility for correcting sizes reported by the NetBeans profiler library
 * when compressed oops are enabled. The library always assumes 8-byte object references,
 * but with compressed oops (default for heaps &lt; 32 GB) references are 4 bytes.
 */
public final class CompressedOopsCorrector {

    private static final int UNCOMPRESSED_REF_SIZE = 8;
    private static final int COMPRESSED_REF_SIZE = 4;
    private static final int ARRAY_HEADER_SIZE = 16;

    private CompressedOopsCorrector() {
    }

    /**
     * Returns the correct shallow size for an instance, adjusting for compressed oops
     * where the NetBeans library incorrectly uses 8-byte references instead of 4-byte.
     */
    @SuppressWarnings("unchecked")
    public static long correctedShallowSize(Instance instance, boolean compressedOops) {
        if (!compressedOops) {
            return instance.getSize();
        }
        if (instance instanceof ObjectArrayInstance arrayInstance) {
            return ARRAY_HEADER_SIZE + (long) arrayInstance.getLength() * COMPRESSED_REF_SIZE;
        }
        List<FieldValue> fieldValues = (List<FieldValue>) instance.getFieldValues();
        int objectRefCount = 0;
        for (FieldValue fv : fieldValues) {
            if (fv instanceof ObjectFieldValue) {
                objectRefCount++;
            }
        }
        long overcount = (long) objectRefCount * (UNCOMPRESSED_REF_SIZE - COMPRESSED_REF_SIZE);
        return instance.getSize() - overcount;
    }

    /**
     * Applies the correction ratio to a retained size value.
     * When compressed oops are disabled, returns the raw value unchanged.
     */
    public static long correctedRetainedSize(long rawRetained, boolean compressedOops, double correctionRatio) {
        if (!compressedOops) {
            return rawRetained;
        }
        return (long) (rawRetained * correctionRatio);
    }

    /**
     * Applies the correction ratio to a total class size value (e.g. from {@code getAllInstancesSize()}).
     * When compressed oops are disabled, returns the raw value unchanged.
     */
    public static long correctedTotalSize(long rawTotal, boolean compressedOops, double correctionRatio) {
        if (!compressedOops) {
            return rawTotal;
        }
        return (long) (rawTotal * correctionRatio);
    }

    /**
     * Corrects the total heap live bytes by subtracting the overcount.
     * When compressed oops are disabled, returns the raw value unchanged.
     */
    public static long correctedTotalHeap(long rawTotalBytes, boolean compressedOops, long totalOvercount) {
        if (!compressedOops) {
            return rawTotalBytes;
        }
        return rawTotalBytes - totalOvercount;
    }

    /**
     * Computes the correction ratio: correctedTotal / rawTotal.
     * This ratio can be applied to any retained or total size to approximate the corrected value.
     */
    public static double computeCorrectionRatio(long rawTotalHeapBytes, long totalOvercount) {
        if (rawTotalHeapBytes <= 0) {
            return 1.0;
        }
        return (double) (rawTotalHeapBytes - totalOvercount) / rawTotalHeapBytes;
    }

    /**
     * Computes the total overcount across all instances in the heap.
     * The NetBeans library uses 8-byte references even when compressed oops are
     * enabled (4-byte refs). This method sums up the difference for every instance.
     */
    @SuppressWarnings("unchecked")
    public static long computeTotalOvercount(Heap heap) {
        long overcount = 0;
        List<JavaClass> allClasses = (List<JavaClass>) heap.getAllClasses();
        for (JavaClass jc : allClasses) {
            List<Instance> instances = (List<Instance>) jc.getInstances();
            for (Instance inst : instances) {
                if (inst instanceof ObjectArrayInstance arr) {
                    overcount += (long) arr.getLength() * (UNCOMPRESSED_REF_SIZE - COMPRESSED_REF_SIZE);
                } else {
                    List<FieldValue> fieldValues = (List<FieldValue>) inst.getFieldValues();
                    for (FieldValue fv : fieldValues) {
                        if (fv instanceof ObjectFieldValue) {
                            overcount += (UNCOMPRESSED_REF_SIZE - COMPRESSED_REF_SIZE);
                        }
                    }
                }
            }
        }
        return overcount;
    }

    /**
     * Returns the reference size in bytes depending on whether compressed oops are enabled.
     */
    public static long referenceSize(boolean compressedOops) {
        return compressedOops ? COMPRESSED_REF_SIZE : UNCOMPRESSED_REF_SIZE;
    }
}
