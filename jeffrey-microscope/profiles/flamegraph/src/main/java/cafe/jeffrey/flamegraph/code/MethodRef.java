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

import java.util.regex.Pattern;

/**
 * A method as a reader names it from source -- {@code com.acme.Checkout#checkout} -- matched against
 * the names the JVM actually runs it under.
 * <p>
 * Source and runtime disagree in ways that make a plain string match miss most of a method's cost:
 * a lambda body runs as {@code lambda$checkout$1}, a Kotlin lambda or coroutine as a class
 * {@code Checkout$checkout$1}, a default-argument overload as {@code checkout$default}, a nested class
 * is {@code Outer$Inner} in the binary and {@code Outer.Inner} in the source, and a Kotlin top-level
 * function lives on {@code CheckoutKt}. A reference matches all of those, and every answer built on it
 * lists the runtime names it merged, so the reader can see exactly what was counted.
 *
 * @param className  the class, binary ({@code Outer$Inner}) or source ({@code Outer.Inner}) spelling
 * @param methodName the method, or {@code null} for every method of the class and its nested classes
 */
public record MethodRef(String className, String methodName) {

    private static final char NESTED_SEPARATOR = '$';
    private static final char SOURCE_SEPARATOR = '.';
    private static final String METHOD_SEPARATOR = "#";
    private static final String KOTLIN_FACADE_SUFFIX = "Kt";

    /**
     * The synthetic names javac and kotlinc give code written inside a method or generated for it:
     * {@code %1$s} is the method's name, quoted.
     */
    private static final String SYNTHETIC_NAMES = "lambda\\$%1$s\\$\\d+"
            + "|%1$s\\$lambda[$-]\\d+(\\$\\d+)?"
            + "|%1$s\\$default"
            + "|access\\$%1$s(\\$\\d+)?"
            + "|%1$s\\$suspendImpl";

    public MethodRef {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className must not be blank");
        }
        className = className.strip();
        if (className.contains(METHOD_SEPARATOR) || className.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Not a class name: '" + className
                    + "'. Pass the class and the method apart, or as Class#method");
        }
        methodName = methodName == null || methodName.isBlank() ? null : methodName.strip();
    }

    /**
     * {@code com.acme.Checkout#checkout}, or a bare class name for the whole class.
     */
    public static MethodRef parse(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("A method reference must not be blank");
        }
        String trimmed = reference.strip();
        int separator = trimmed.indexOf(METHOD_SEPARATOR);
        if (separator < 0) {
            return new MethodRef(trimmed, null);
        }
        return new MethodRef(trimmed.substring(0, separator), trimmed.substring(separator + 1));
    }

    /** Whether the reference names a whole class rather than one method of it. */
    public boolean wholeClass() {
        return methodName == null;
    }

    /** {@code Class#method}, or the class alone. */
    public String displayName() {
        return methodName == null ? className : className + METHOD_SEPARATOR + methodName;
    }

    /** A matcher for this reference; build it once and ask it of every frame. */
    public Matcher matcher() {
        return new Matcher(this);
    }

    /**
     * Matches a reference against frames, with the patterns compiled once.
     */
    public static final class Matcher {

        private final String className;
        private final String sourceClassName;
        private final String nestedPrefix;
        private final String kotlinFacade;
        private final String methodName;
        private final String methodHolderPrefix;
        private final Pattern syntheticNames;

        private Matcher(MethodRef ref) {
            this.className = ref.className();
            this.sourceClassName = sourceName(ref.className());
            this.nestedPrefix = ref.className() + NESTED_SEPARATOR;
            this.kotlinFacade = ref.className() + KOTLIN_FACADE_SUFFIX;
            this.methodName = ref.methodName();
            this.methodHolderPrefix = methodName == null ? null : nestedPrefix + methodName + NESTED_SEPARATOR;
            this.syntheticNames = methodName == null
                    ? null
                    : Pattern.compile(SYNTHETIC_NAMES.formatted(Pattern.quote(methodName)));
        }

        /** Whether this Java frame runs code the reference names. */
        public boolean matches(MethodKey frame) {
            String frameClass = frame.className();
            if (frameClass.isEmpty()) {
                return false;
            }
            boolean declaringClass = frameClass.equals(className)
                    || frameClass.equals(kotlinFacade)
                    || sourceName(frameClass).equals(sourceClassName);
            if (methodName == null) {
                return declaringClass || frameClass.startsWith(nestedPrefix);
            }
            if (declaringClass) {
                String frameMethod = frame.methodName();
                return frameMethod.equals(methodName) || syntheticNames.matcher(frameMethod).matches();
            }
            // A Kotlin lambda or coroutine body is a class named after the function that holds it.
            return frameClass.startsWith(methodHolderPrefix);
        }

        private static String sourceName(String binaryName) {
            return binaryName.replace(NESTED_SEPARATOR, SOURCE_SEPARATOR);
        }
    }
}
