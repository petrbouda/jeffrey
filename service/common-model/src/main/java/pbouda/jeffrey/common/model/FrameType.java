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

package pbouda.jeffrey.common.model;

public enum FrameType {
    C1_COMPILED("C1 compiled", true, "JAVA C1-compiled", "#cce880"),
    NATIVE("Native", false, "Native", "#ffa6a6"),
    CPP("C++", false, "C++ (JVM)", "#e3ed6d"),
    INTERPRETED("Interpreted", true, "Interpreted (JAVA)", "#b2e1b2"),
    JIT_COMPILED("JIT compiled", true, "JIT-compiled (JAVA)", "#94f25a"),
    INLINED("Inlined", true, "Inlined (JAVA)", "#8eeded"),
    KERNEL("Kernel", false, "Kernel", "#f2af5e"),
    THREAD_NAME_SYNTHETIC("Thread Name (Synthetic)", "#e17e5a"),
    ALLOCATED_OBJECT_SYNTHETIC("Allocated Object (Synthetic)", "#00b6ff"),
    ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC("Allocated in New TLAB (Synthetic)", "#ADE8F4"),
    ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC("Allocated Outside TLAB (Synthetic)", "#00B4D8"),
    BLOCKING_OBJECT_SYNTHETIC("Blocking Object (Synthetic)", "#e17e5a"),
    LAMBDA_SYNTHETIC("Lambda (Synthetic)", "#b3c6ff"),
    UNKNOWN("Unknown", false, "Unknown", "#000000"),
    HIGHLIGHTED_WARNING("Highlighted Warning", "#ed0202");

    private static final FrameType[] VALUES = values();

    private final String code;
    private final String title;
    private final String color;
    private final boolean synthetic;
    private final boolean javaFrame;

    /**
     * For SYNTHETIC frames
     *
     * @param title description of the frame type
     * @param color color of the frame type
     */
    FrameType(String title, String color) {
        this(null, false, title, color, true);
    }

    /**
     * For REGULAR frames
     *
     * @param code code of the frame type
     * @param javaFrame whether the frame is one of the Java frames
     * @param title description of the frame type
     * @param color color of the frame type
     */
    FrameType(String code, boolean javaFrame, String title, String color) {
        this(code, javaFrame, title, color, false);
    }

    /**
     * For both SYNTHETIC and REGULAR frames
     *
     * @param code code of the frame type
     * @param javaFrame whether the frame is one of the Java frames
     * @param title description of the frame type
     * @param color color of the frame type
     * @param synthetic whether the frame is synthetic or regular
     */
    FrameType(String code, boolean javaFrame, String title, String color, boolean synthetic) {
        this.code = code;
        this.title = title;
        this.color = color;
        this.synthetic = synthetic;
        this.javaFrame = javaFrame;
    }

    public static FrameType fromCode(String code) {
        for (FrameType value : VALUES) {
            if (value.name().equals(code) || value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        throw new RuntimeException("Frame type does not exists: " + code);
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    public boolean isJavaFrame() {
        return javaFrame;
    }

    public String color() {
        return color;
    }

    public String title() {
        return title;
    }
}
