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

import cafe.jeffrey.shared.common.model.EventTypeName;

import java.util.List;

/**
 * Which stack frames mean a promoted file read was the class loader's work.
 * <p>
 * The question this answers is not "what file was read" — it cannot be. A fat jar is read by the
 * class loader, by {@code ServiceLoader} scanning for providers, by {@code getResourceAsStream}, and
 * by a library unpacking its own native library, and all four arrive at the same
 * {@code java.util.zip.ZipFile$Source.readAt} leaf frame against the same {@code .jar} path. In one
 * measured JVM, 2534 of 2628 jar reads were a driver unpacking its {@code .so} and only ~68 were
 * class loading. Neither the path nor the leaf frame separates them.
 * <p>
 * What does separate them is a class-loader frame further down the stack — in that measurement it
 * sat between 4 and 14 frames from the leaf. So the derivation walks a promoted I/O event's stack
 * and looks for one of the prefixes below.
 * <p>
 * The verdict is deliberately one-sided. A hit means class loading; a miss means only that nothing
 * said so, which is also what a recording that captured no stack trace produces. {@code io_origin}
 * is therefore NULL for "not known to be class loading", never for "known not to be" — the UI must
 * not present the absence as a negative finding.
 */
final class ClassLoadingFrames {

    /**
     * Class-name prefixes that appear in a class-loading stack.
     * <p>
     * {@code java.lang.Class} is deliberately absent, on both counts: as a prefix it would also
     * match {@code java.lang.ClassValue} and {@code java.lang.ClassLoader}, and
     * {@code Class.forName} reaches the file through a loader anyway, so the loader frame is already
     * on the stack when it matters.
     */
    static final List<String> CLASS_NAME_PREFIXES = List.of(
            // BuiltinClassLoader, ClassLoaders, Resource, URLClassPath — the modern path, and the
            // one that produced every genuine class-loading read in the measurement above.
            "jdk.internal.loader.",
            "java.lang.ClassLoader",
            "java.net.URLClassLoader",
            "java.security.SecureClassLoader",
            // A Spring Boot fat jar loads through its own loader rather than the built-in one.
            "org.springframework.boot.loader.",
            // Pre-JDK-9 launcher, still reachable through an old embedded loader.
            "sun.misc.Launcher");

    /** The origin written to {@code trace_spans.io_origin} when one of the prefixes above matches. */
    static final String CLASS_LOADING_ORIGIN = "CLASS_LOADING";

    /**
     * The promoted event types this verdict is asked about — the I/O family, and only it.
     * <p>
     * A narrower set than {@link BlockingLeafSpans#EVENT_TYPES} on purpose. A park or a monitor wait
     * inside a class loader would match the frames above just as happily, and the column is
     * {@code io_origin}: saying a park's <em>I/O</em> was class loading is not a true sentence, and a
     * later reader would be right to act on it. Sockets stay in because a loader can be reading over
     * one.
     */
    static final List<String> EVENT_TYPES = List.of(
            EventTypeName.FILE_READ,
            EventTypeName.FILE_WRITE,
            EventTypeName.FILE_FORCE,
            EventTypeName.SOCKET_READ,
            EventTypeName.SOCKET_WRITE);

    private ClassLoadingFrames() {
    }
}
