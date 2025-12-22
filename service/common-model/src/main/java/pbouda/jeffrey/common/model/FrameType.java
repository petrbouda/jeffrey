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
    C1_COMPILED("C1 compiled", true, "JAVA C1-compiled"),
    NATIVE("Native", false, "Native"),
    CPP("C++", false, "C++ (JVM)"),
    INTERPRETED("Interpreted", true, "Interpreted (JAVA)"),
    JIT_COMPILED("JIT compiled", true, "JIT-compiled (JAVA)"),
    INLINED("Inlined", true, "Inlined (JAVA)"),
    KERNEL("Kernel", false, "Kernel"),
    THREAD_NAME_SYNTHETIC("Thread Name (Synthetic)"),
    ALLOCATED_OBJECT_SYNTHETIC("Allocated Object (Synthetic)"),
    ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC("Allocated in New TLAB (Synthetic)"),
    ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC("Allocated Outside TLAB (Synthetic)"),
    BLOCKING_OBJECT_SYNTHETIC("Blocking Object (Synthetic)"),
    LAMBDA_SYNTHETIC("Lambda (Synthetic)"),
    UNKNOWN("Unknown", false, "Unknown"),
    HIGHLIGHTED_WARNING("Highlighted Warning");

    private static final FrameType[] VALUES = values();

    private final String code;
    private final String title;
    private final boolean synthetic;
    private final boolean javaFrame;

    /**
     * For SYNTHETIC frames
     *
     * @param title description of the frame type
     */
    FrameType(String title) {
        this(null, false, title, true);
    }

    /**
     * For REGULAR frames
     *
     * @param code code of the frame type
     * @param javaFrame whether the frame is one of the Java frames
     * @param title description of the frame type
     */
    FrameType(String code, boolean javaFrame, String title) {
        this(code, javaFrame, title, false);
    }

    /**
     * For both SYNTHETIC and REGULAR frames
     *
     * @param code code of the frame type
     * @param javaFrame whether the frame is one of the Java frames
     * @param title description of the frame type
     * @param synthetic whether the frame is synthetic or regular
     */
    FrameType(String code, boolean javaFrame, String title, boolean synthetic) {
        this.code = code;
        this.title = title;
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

    public String title() {
        return title;
    }
}
