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

package pbouda.jeffrey.frameir.frame;

import pbouda.jeffrey.frameir.FrameType;
import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;

import java.util.IdentityHashMap;

public class FrameNameBuilder {

    /**
     * Cache for the class names to avoid multiple parsing of the same class name (JFR Symbols).
     * There is no removing item from the cache, ensure that the {@link FrameNameBuilder} has a limited lifetime.
     */
    private final IdentityHashMap<Object, String> cachedClasses = new IdentityHashMap<>();

    /**
     * Standard way of naming the frames, it could be interesting for the majority of implemetations.
     *
     * @param stackTrace currently processed stacktrace.
     * @param frame      currently processed frame.
     * @param frameType  type of the current frame.
     * @return standard name of the current frame.
     */
    public String generateName(JfrStackTrace stackTrace, JfrStackFrame frame, FrameType frameType) {
        return switch (frameType) {
            case JIT_COMPILED, C1_COMPILED, INTERPRETED, INLINED -> {
                JfrClass jfrClass = frame.method().clazz();
                yield resolvedCachedName(jfrClass) + "#" + frame.method().name();
            }
            case CPP, KERNEL, NATIVE -> frame.method().name();
            case THREAD_NAME_SYNTHETIC -> methodNameBasedThread(stackTrace.sampledThread());
            case UNKNOWN -> throw new IllegalArgumentException("Unknown Frame occurred in JFR");
            default -> throw new IllegalStateException("Unexpected value: " + frameType);
        };
    }

    /**
     * Caches the name of the class to avoid multiple parsing of the same class name (JFR Symbols).
     * Identical class objects should have the same names.
     *
     * @param clazz class that is resolved for the current frame.
     * @return resolved name of the class.
     */
    private String resolvedCachedName(JfrClass clazz) {
        Object key = clazz.original();
        String value = cachedClasses.get(key);
        if (value == null) {
            String newValue = clazz.name();
            cachedClasses.put(key, newValue);
            return newValue;
        }
        return value;
    }

    /**
     * Standard way of naming the frames, it could be interesting for the majority of implemetations.
     *
     * @param stackTrace stacktrace that keeps the frame.
     * @param frame      currently processed frame.
     * @return standard name of the current frame.
     */
    public String generateName(JfrStackTrace stackTrace, JfrStackFrame frame) {
        FrameType frameType = FrameType.fromCode(frame.type());
        return generateName(stackTrace, frame, frameType);
    }

    public static String methodNameBasedThread(JfrThread thread) {
        if (thread.javaThreadId() > 0) {
            return thread.javaName() + " (" + thread.javaThreadId() + ")";
        } else {
            return thread.osName() + " (" + thread.osThreadId() + ")";
        }
    }
}
