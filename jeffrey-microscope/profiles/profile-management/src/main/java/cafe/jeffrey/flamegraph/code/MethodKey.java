/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.flamegraph.code;

/**
 * One method as the recording names it: the class with any hidden-class address already stripped,
 * and the method. A native, C++ or kernel frame has no class and is keyed by its symbol alone, with
 * an empty class name.
 *
 * @param className  the declaring class, binary name ({@code com.acme.Checkout$Inner}); empty for a
 *                   frame that is not Java
 * @param methodName the method, as the bytecode names it ({@code lambda$checkout$1}, {@code <init>})
 */
public record MethodKey(String className, String methodName) implements Comparable<MethodKey> {

    private static final String SEPARATOR = "#";

    public MethodKey {
        if (className == null) {
            throw new IllegalArgumentException("className must not be null; a non-Java frame has an empty one");
        }
        if (methodName == null) {
            throw new IllegalArgumentException("methodName must not be null");
        }
    }

    /** {@code Class#method}, the way the flamegraph exports name a Java frame; the bare symbol otherwise. */
    public String displayName() {
        return className.isEmpty() ? methodName : className + SEPARATOR + methodName;
    }

    @Override
    public int compareTo(MethodKey other) {
        int byClass = className.compareTo(other.className);
        return byClass != 0 ? byClass : methodName.compareTo(other.methodName);
    }
}
