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

import java.util.Set;
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
    /** A javac lambda body, {@code lambda$<method>$<n>}: code written inside {@code <method>}. */
    private static final Pattern JAVAC_LAMBDA = Pattern.compile("lambda\\$([^$]+)\\$\\d+");
    /** What javac puts in a lambda's name when it is written outside a method: an initializer, a constructor. */
    private static final Set<String> NOT_A_METHOD = Set.of("static", "new");

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

    /**
     * The reference a developer writes for this frame: a named nested class in source spelling
     * ({@code com.acme.Checkout$Inner} becomes {@code com.acme.Checkout.Inner}), so the name is the one
     * the repository spells and an answer keyed by it greps there. A class with a segment no source can
     * spell — an anonymous or local class ({@code Outer$1}), a generated one ({@code Foo$$Proxy}) — keeps
     * its binary name, which the matcher reads as well. A javac lambda body ({@code lambda$checkout$1}) is
     * named by the method it is written in ({@code checkout}), which the matcher widens back to that method
     * and its lambdas; any other method keeps the name the bytecode gives it.
     *
     * @param frame a Java frame, with a class
     */
    public static MethodRef of(MethodKey frame) {
        return new MethodRef(sourceSpelling(frame.className()), sourceMethod(frame.methodName()));
    }

    private static String sourceMethod(String methodName) {
        var lambda = JAVAC_LAMBDA.matcher(methodName);
        if (lambda.matches() && !NOT_A_METHOD.contains(lambda.group(1))) {
            return lambda.group(1);
        }
        return methodName;
    }

    private static String sourceSpelling(String binaryName) {
        int lastDot = binaryName.lastIndexOf(SOURCE_SEPARATOR);
        String simpleNames = binaryName.substring(lastDot + 1);
        if (simpleNames.indexOf(NESTED_SEPARATOR) < 0) {
            return binaryName;
        }
        for (String segment : simpleNames.split(Pattern.quote(String.valueOf(NESTED_SEPARATOR)), -1)) {
            if (!isSourceIdentifier(segment)) {
                return binaryName;
            }
        }
        return binaryName.replace(NESTED_SEPARATOR, SOURCE_SEPARATOR);
    }

    private static boolean isSourceIdentifier(String segment) {
        if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))) {
            return false;
        }
        return segment.chars().allMatch(Character::isJavaIdentifierPart);
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

        private final String sourceClassName;
        private final String kotlinFacade;
        private final String methodName;
        private final String methodHolderSuffix;
        private final Pattern syntheticNames;

        private Matcher(MethodRef ref) {
            this.sourceClassName = sourceName(ref.className());
            this.kotlinFacade = sourceClassName + KOTLIN_FACADE_SUFFIX;
            this.methodName = ref.methodName();
            this.methodHolderSuffix = methodName == null ? null : methodName + NESTED_SEPARATOR;
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
            String sourceFrameClass = sourceName(frameClass);
            boolean declaringClass = sourceFrameClass.equals(sourceClassName)
                    || sourceFrameClass.equals(kotlinFacade);
            if (methodName == null) {
                return declaringClass || matchesNestedClass(frameClass);
            }
            if (declaringClass) {
                String frameMethod = frame.methodName();
                return frameMethod.equals(methodName) || syntheticNames.matcher(frameMethod).matches();
            }
            return matchesNestedClass(frameClass);
        }

        /** Normalize enclosing class names at actual nesting boundaries, keeping method names as recorded. */
        private boolean matchesNestedClass(String binaryName) {
            for (int boundary = binaryName.indexOf(NESTED_SEPARATOR); boundary >= 0;
                 boundary = binaryName.indexOf(NESTED_SEPARATOR, boundary + 1)) {
                boolean enclosedByReference = spelledAs(binaryName, boundary, sourceClassName)
                        || spelledAs(binaryName, boundary, kotlinFacade);
                if (enclosedByReference
                        && (methodName == null || binaryName.startsWith(methodHolderSuffix, boundary + 1))) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Whether the binary name's first {@code length} characters spell the source name, each {@code $}
         * read as a dot: {@link #sourceName} of that prefix compared, without building it for every frame.
         */
        private static boolean spelledAs(String binaryName, int length, String source) {
            if (source.length() != length) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                char c = binaryName.charAt(i);
                if ((c == NESTED_SEPARATOR ? SOURCE_SEPARATOR : c) != source.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        private static String sourceName(String binaryName) {
            return binaryName.replace(NESTED_SEPARATOR, SOURCE_SEPARATOR);
        }
    }
}
