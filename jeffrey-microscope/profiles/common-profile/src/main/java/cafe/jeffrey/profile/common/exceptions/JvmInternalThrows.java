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

package cafe.jeffrey.profile.common.exceptions;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Which throws are the JVM's own resolution and linking machinery rather than the application's.
 * <p>
 * Two rules, and a throw is {@link ThrowOrigin#JVM_INTERNAL} when either matches:
 * <ol>
 *     <li><b>The class.</b> The thrown class is one of {@link #RESOLUTION_FAILURE_CLASSES}.</li>
 *     <li><b>The thrower.</b> The frame that contained the {@code throw} — the first frame below the
 *     constructor chain, see {@link ThrowFrames} — sits in {@link #MACHINERY_FRAME_PREFIXES}: a
 *     class loader probing a jar, or the {@code java.lang.invoke} layer linking a call site. An
 *     {@code IOException} raised there is the loader's business, whatever its class.</li>
 * </ol>
 * Everything else, libraries included, is {@link ThrowOrigin#APPLICATION}: a timeout inside Netty or
 * a logback failure is exactly what the application's owner needs to see.
 */
public final class JvmInternalThrows {

    /**
     * Throws that mean the JVM or JDK failed to resolve or link something. Three closed hierarchies,
     * enumerated because SQL cannot ask about a class hierarchy: {@link LinkageError}, the
     * {@code java.lang.invoke} throwables, and {@link ReflectiveOperationException}.
     * <p>
     * The MethodHandle layer raises these by the handful while linking an {@code invokedynamic} call
     * site, and a library probing for an optional dependency does the same through
     * {@code Class.forName}. They land on the request thread inside whatever work triggered them, so
     * the attribution is right — they are just not something a reader can act on.
     * <p>
     * Taken as the closure of the three roots over every module in the JDK, so a new entry is only
     * needed if the JDK itself adds one. Deliberately absent: cancellation and timeouts, the resource
     * errors, and ordinary application logic — those are what a reader is looking for.
     * <p>
     * A list rather than a set because the trace derivation binds it as a SQL {@code IN} list.
     */
    public static final List<String> RESOLUTION_FAILURE_CLASSES = List.of(
            // java.lang.LinkageError and every subclass
            "java.lang.LinkageError",
            "java.lang.BootstrapMethodError",
            "java.lang.ClassCircularityError",
            "java.lang.ClassFormatError",
            "java.lang.UnsupportedClassVersionError",
            "java.lang.ExceptionInInitializerError",
            "java.lang.IncompatibleClassChangeError",
            "java.lang.AbstractMethodError",
            "java.lang.IllegalAccessError",
            "java.lang.InstantiationError",
            "java.lang.NoSuchFieldError",
            "java.lang.NoSuchMethodError",
            "java.lang.NoClassDefFoundError",
            "java.lang.UnsatisfiedLinkError",
            "java.lang.VerifyError",
            "java.lang.reflect.GenericSignatureFormatError",
            // java.lang.invoke throwables
            "java.lang.invoke.WrongMethodTypeException",
            "java.lang.invoke.LambdaConversionException",
            "java.lang.invoke.StringConcatException",
            "java.lang.invoke.InvokerBytecodeGenerator$BytecodeGenerationException",
            // java.lang.ReflectiveOperationException and every subclass
            "java.lang.ReflectiveOperationException",
            "java.lang.ClassNotFoundException",
            "java.lang.IllegalAccessException",
            "java.lang.InstantiationException",
            "java.lang.NoSuchFieldException",
            "java.lang.NoSuchMethodException",
            "java.lang.reflect.InvocationTargetException",
            "java.lang.reflect.Proxy$InvocationException");

    /** The method-handle and lambda-linking layer, which throws and catches while binding call sites. */
    private static final String INVOKE_PACKAGE_PREFIX = "java.lang.invoke.";

    /**
     * Where a thrower makes the throw the JVM's: the class loaders, and the {@code java.lang.invoke}
     * linking layer.
     */
    public static final List<String> MACHINERY_FRAME_PREFIXES = Stream.concat(
                    ClassLoadingFramePrefixes.PREFIXES.stream(),
                    Stream.of(INVOKE_PACKAGE_PREFIX))
            .toList();

    private static final Set<String> RESOLUTION_FAILURES = Set.copyOf(RESOLUTION_FAILURE_CLASSES);

    private JvmInternalThrows() {
    }

    /**
     * @param thrownClass   the class the event recorded as thrown
     * @param throwingFrame the frame that contained the {@code throw}; null when the recording
     *                      captured no stack, which leaves only the class rule to decide
     */
    public static ThrowOrigin classify(String thrownClass, ThrowFrame throwingFrame) {
        if (isResolutionFailure(thrownClass) || isThrownByMachinery(throwingFrame)) {
            return ThrowOrigin.JVM_INTERNAL;
        }
        return ThrowOrigin.APPLICATION;
    }

    public static boolean isResolutionFailure(String thrownClass) {
        return thrownClass != null && RESOLUTION_FAILURES.contains(thrownClass);
    }

    private static boolean isThrownByMachinery(ThrowFrame throwingFrame) {
        return throwingFrame != null && throwingFrame.startsWithAny(MACHINERY_FRAME_PREFIXES);
    }
}
