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

package cafe.jeffrey.profile.heapdump.model;

/**
 * Categories of class-loader leak patterns. The catalog is intentionally small and
 * matches the canonical Tomcat-redeploy / classloader-pinning failure modes.
 */
public enum HintKind {
    /** {@code ThreadLocal} or {@code InheritableThreadLocal} retains an object whose class was loaded by the leaking loader. */
    THREAD_LOCAL,
    /** {@code java.sql.DriverManager} retains a registered JDBC driver loaded by the leaking loader. */
    JDBC_DRIVER,
    /** GC root is a JNI global/local — native code is holding a reference. */
    JNI_GLOBAL,
    /** {@code java.util.ServiceLoader} is keeping the loader alive (often via a static cache). */
    SERVICE_LOADER,
    /** A static {@code Logger} / {@code LogManager} entry references the loader. */
    LOGGER,
    /** A {@code Thread}'s {@code contextClassLoader} (often on a thread-pool thread) pins the loader. */
    CONTEXT_CLASSLOADER
}
