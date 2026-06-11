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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Try-once probe of the Apache Arrow runtime. Arrow needs three pieces to work:
 * an allocation manager (unsafe-based) for off-heap vectors, the C Data Interface
 * JNI library (bundled per-platform in {@code arrow-c-data}), and reflective access
 * to {@code java.nio.DirectByteBuffer} for C struct manipulation, which requires the
 * {@code --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED}
 * JVM argument. If any of them fails to initialize, the Arrow ingestion path is
 * unavailable and callers must fall back to the row-based appender writer.
 */
public final class ArrowRuntimeSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ArrowRuntimeSupport.class);

    private static final long PROBE_ALLOCATOR_LIMIT_BYTES = 1024 * 1024;

    private static final boolean AVAILABLE = probe();

    private ArrowRuntimeSupport() {
    }

    /**
     * @return true when Arrow off-heap allocation and the C Data Interface JNI library
     * are both usable on the current platform
     */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    private static boolean probe() {
        try (BufferAllocator allocator = new RootAllocator(PROBE_ALLOCATOR_LIMIT_BYTES)) {
            JniLoader.get().ensureLoaded();
            // Exercises the exact operations of an exported batch: allocating the C struct
            // (off-heap allocation manager) and wrapping it in a DirectByteBuffer
            // (fails without the java.nio add-opens).
            try (ArrowArrayStream arrowStream = ArrowArrayStream.allocateNew(allocator)) {
                return true;
            }
        } catch (Throwable t) {
            LOG.warn("Arrow runtime is not available, columnar event ingestion is disabled: reason={}", t.toString());
            return false;
        }
    }
}
