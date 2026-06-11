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

package cafe.jeffrey.provider.profile.jdbc;

import org.apache.arrow.c.ArrowArrayStream;
import org.apache.arrow.c.jni.JniLoader;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;

/**
 * Try-once probe of the Apache Arrow runtime. Arrow needs three pieces to work:
 * an allocation manager (unsafe-based) for off-heap vectors, the C Data Interface
 * JNI library (bundled per-platform in {@code arrow-c-data}), and reflective access
 * to {@code java.nio.DirectByteBuffer} for C struct manipulation, which requires the
 * {@code --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED}
 * JVM argument. The Arrow path is the only writer for the {@code events} table, so a
 * failed probe is fatal: {@link #ensureAvailable()} throws at provider/writer
 * construction instead of letting the ingestion fail mid-parse.
 */
public final class ArrowRuntimeSupport {

    private static final long PROBE_ALLOCATOR_LIMIT_BYTES = 1024 * 1024;

    private static final String UNAVAILABLE_MESSAGE =
            "Apache Arrow runtime failed to initialize — columnar event ingestion cannot start. " +
            "Run the JVM with '--add-opens=java.base/java.nio=ALL-UNNAMED' " +
            "('java -jar' picks it up automatically from the Add-Opens attribute in the fat-jar manifest) " +
            "and make sure the platform is supported by the bundled Arrow C Data native libraries " +
            "(linux x86_64/aarch64, macOS x86_64/aarch64, windows x86_64).";

    private static final Throwable PROBE_FAILURE = probe();

    private ArrowRuntimeSupport() {
    }

    /**
     * Verifies that Arrow off-heap allocation and the C Data Interface JNI library are
     * usable on the current platform.
     *
     * @throws IllegalStateException with an actionable message when the Arrow runtime
     *                               cannot be initialized
     */
    public static void ensureAvailable() {
        if (PROBE_FAILURE != null) {
            throw new IllegalStateException(UNAVAILABLE_MESSAGE, PROBE_FAILURE);
        }
    }

    private static Throwable probe() {
        try (BufferAllocator allocator = new RootAllocator(PROBE_ALLOCATOR_LIMIT_BYTES)) {
            JniLoader.get().ensureLoaded();
            // Exercises the exact operations of an exported batch: allocating the C struct
            // (off-heap allocation manager) and wrapping it in a DirectByteBuffer
            // (fails without the java.nio add-opens).
            try (ArrowArrayStream arrowStream = ArrowArrayStream.allocateNew(allocator)) {
                return null;
            }
        } catch (Throwable t) {
            return t;
        }
    }
}
