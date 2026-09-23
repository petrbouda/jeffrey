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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.util.Set;
import cafe.jeffrey.profile.heapdump.model.LoaderType;

/**
 * Maps a class loader's declaring class name to a coarse {@link LoaderType}.
 * Drives the type badge on the Class Loaders page and the "hide JDK loaders"
 * toolbar toggle on the Hierarchy tab. Heuristic only — the JDK does not
 * tag loaders by category.
 */
public final class LoaderTypeClassifier {

    private static final long BOOTSTRAP_LOADER_ID = 0L;

    private static final Set<String> PLATFORM_CLASS_NAMES = Set.of(
            "jdk.internal.loader.ClassLoaders$PlatformClassLoader");

    private static final Set<String> SYSTEM_CLASS_NAMES = Set.of(
            "jdk.internal.loader.ClassLoaders$AppClassLoader",
            "sun.misc.Launcher$AppClassLoader");

    private static final Set<String> WEB_CLASS_NAME_SUBSTRINGS = Set.of(
            "WebappClassLoader",
            "ParallelWebappClassLoader",
            "LaunchedURLClassLoader",
            "RestartClassLoader");

    private static final Set<String> OSGI_CLASS_NAME_PREFIXES = Set.of(
            "org.eclipse.osgi",
            "org.apache.felix",
            "org.osgi.");

    private static final Set<String> APP_CLASS_NAME_SUBSTRINGS = Set.of(
            "URLClassLoader",
            "SecureClassLoader");

    private LoaderTypeClassifier() {
    }

    public static LoaderType classify(long loaderId, String loaderClassName) {
        if (loaderId == BOOTSTRAP_LOADER_ID) {
            return LoaderType.BOOTSTRAP;
        }
        if (loaderClassName == null) {
            return LoaderType.CUSTOM;
        }
        if (PLATFORM_CLASS_NAMES.contains(loaderClassName)) {
            return LoaderType.PLATFORM;
        }
        if (SYSTEM_CLASS_NAMES.contains(loaderClassName)) {
            return LoaderType.SYSTEM;
        }
        if (matchesAnySubstring(loaderClassName, WEB_CLASS_NAME_SUBSTRINGS)) {
            return LoaderType.WEB;
        }
        if (matchesAnyPrefix(loaderClassName, OSGI_CLASS_NAME_PREFIXES)) {
            return LoaderType.OSGI;
        }
        if (matchesAnySubstring(loaderClassName, APP_CLASS_NAME_SUBSTRINGS)) {
            return LoaderType.APP;
        }
        return LoaderType.CUSTOM;
    }

    private static boolean matchesAnySubstring(String value, Set<String> needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyPrefix(String value, Set<String> prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
