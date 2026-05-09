/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
