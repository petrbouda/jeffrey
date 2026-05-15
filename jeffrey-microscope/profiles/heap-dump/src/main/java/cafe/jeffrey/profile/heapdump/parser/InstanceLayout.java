/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
package cafe.jeffrey.profile.heapdump.parser;

/**
 * Per-instance memory layout the parser assumes when computing shallow size.
 *
 * <ul>
 *   <li>{@code objectHeader} / {@code arrayHeader} — bytes occupied by the
 *       JVM-side header, before any payload.</li>
 *   <li>{@code idSize} — HPROF on-disk pointer width, always 4 (32-bit) or
 *       8 (64-bit). Drives how OBJECT fields and OBJECT-array elements are
 *       encoded in the .hprof file.</li>
 *   <li>{@code oopSize} — pointer width on the live JVM heap. Equals
 *       {@code idSize} on 32-bit and on 64-bit without compressed oops;
 *       4 bytes when compressed oops are enabled. References take this
 *       many bytes per slot regardless of the on-disk width.</li>
 *   <li>{@code objectAlignment} — every allocation is rounded up to this
 *       boundary (HotSpot {@code MinObjAlignment}, 8 by default).</li>
 * </ul>
 *
 * Three concrete layouts:
 * <ul>
 *   <li>32-bit JVM (idSize == 4): header 8/12, oopSize 4</li>
 *   <li>64-bit compressed oops: header 16/16, oopSize 4</li>
 *   <li>64-bit uncompressed oops: header 16/24, oopSize 8</li>
 * </ul>
 */
public record InstanceLayout(
        int objectHeader,
        int arrayHeader,
        int idSize,
        int oopSize,
        int objectAlignment) {

    public static InstanceLayout from(int idSize, boolean compressedOops) {
        if (idSize == 4) {
            return new InstanceLayout(8, 12, 4, 4, 8);
        }
        return compressedOops
                ? new InstanceLayout(16, 16, 8, 4, 8)
                : new InstanceLayout(16, 24, 8, 8, 8);
    }

    /**
     * OOP encoding delta: bytes over-counted per reference when the on-disk
     * pointer is wider than the on-heap one (only non-zero with compressed oops).
     */
    public int oopOverheadDelta() {
        return idSize - oopSize;
    }

    public long alignUp(long size) {
        int a = objectAlignment;
        return (size + a - 1) / a * a;
    }
}
