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

package pbouda.jeffrey.generator.flamegraph;

public enum FrameType {
    C1_COMPILED("C1 compiled", "JAVA C1-compiled", "#cce880"),
    NATIVE("Native", "Native", "#e15a5a"),
    CPP("C++", "C++ (JVM)", "#c8c83c"),
    INTERPRETED("Interpreted", "Interpreted (JAVA)", "#b2e1b2"),
    JIT_COMPILED("JIT compiled", "JIT-compiled (JAVA)", "#50e150"),
    INLINED("Inlined", "Inlined (JAVA)", "#50cccc"),
    KERNEL("Kernel", "Kernel", "#e17d00"),
    THREAD_NAME_SYNTHETIC("Thread Name (Synthetic)", "#e17e5a"),
    ALLOCATED_OBJECT_SYNTHETIC("Allocated Object (Synthetic)", "#00b6ff"),
    ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC("Allocated in New TLAB (Synthetic)", "#ADE8F4"),
    ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC("Allocated Outside TLAB (Synthetic)", "#00B4D8"),
    BLOCKING_OBJECT_SYNTHETIC("Blocking Object (Synthetic)", "#e17e5a"),
    UNKNOWN("Unknown", "Unknown", "#000000");

    private static final FrameType[] VALUES = values();

    private final String code;
    private final String title;
    private final String color;

    FrameType(String title, String color) {
        this(null, title, color);
    }

    FrameType(String code, String title, String color) {
        this.code = code;
        this.title = title;
        this.color = color;
    }

    public static FrameType fromCode(String code) {
        for (FrameType value : VALUES) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        throw new RuntimeException("Frame type does not exists: " + code);
    }

    public String color() {
        return color;
    }

    public String title() {
        return title;
    }
}
