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

/**
 * Class-name prefixes that appear in a class-loading stack — the one list behind every reading of
 * "this work was the class loader's": the trace derivation's {@code io_origin} and the JVM-internal
 * throw classification in {@link JvmInternalThrows}.
 */
public final class ClassLoadingFramePrefixes {

    /**
     * {@code java.lang.Class} is deliberately absent, on both counts: as a prefix it would also match
     * {@code java.lang.ClassValue} and {@code java.lang.ClassLoader}, and {@code Class.forName}
     * reaches the file through a loader anyway, so the loader frame is already on the stack when it
     * matters.
     */
    public static final List<String> PREFIXES = List.of(
            // BuiltinClassLoader, ClassLoaders, Resource, URLClassPath — the modern path, and the
            // one that produced every genuine class-loading read the trace derivation measured.
            "jdk.internal.loader.",
            "java.lang.ClassLoader",
            "java.net.URLClassLoader",
            "java.security.SecureClassLoader",
            // A Spring Boot fat jar loads through its own loader rather than the built-in one.
            "org.springframework.boot.loader.",
            // Pre-JDK-9 launcher, still reachable through an old embedded loader.
            "sun.misc.Launcher");

    private ClassLoadingFramePrefixes() {
    }
}
