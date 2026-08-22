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

package cafe.jeffrey.agent.tracing;

import java.lang.System.Logger.Level;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * Finds the tracing runtime that belongs to an instrumented class, or decides once and for all
 * that there is none.
 * <p>
 * The agent cannot link against {@code jeffrey-events}: the agent jar is appended to the system
 * class path, while the library usually lives in a child loader — a Spring Boot fat jar, a web
 * application, a plugin container. Linking would also pin the agent to Java 25, which it is not.
 * So the runtime is resolved through the <em>instrumented class's own</em> loader, which is the one
 * loader guaranteed to see the library that class was compiled against.
 * <p>
 * Resolution can fail for perfectly ordinary reasons — the application does not ship
 * {@code jeffrey-events}, or runs on Java 21 where the release-25 runtime cannot load. Every one of
 * those ends the same way: remember that this class has no runtime, say so once, and let its
 * methods run untouched forever after. An agent that broke an application because a dependency was
 * missing would be a far worse bug than the missing spans.
 */
final class TracedRuntimeBinding {

    private static final System.Logger LOG = System.getLogger(TracedRuntimeBinding.class.getName());

    /** Resolved by name because the agent has no compile-time knowledge of the library. */
    private static final String RUNTIME_CLASS = "cafe.jeffrey.jfr.events.trace.TracedRuntime";
    private static final String RUNTIME_METHOD = "traced";

    /** Stands in for "looked, found nothing" so a failed lookup is cached like any other answer. */
    private static final Object UNAVAILABLE = new Object();

    /**
     * Keyed by the instrumented class, so an entry is collected with the class that owns it and no
     * class loader is kept alive by this cache.
     */
    private static final ClassValue<Object> BINDINGS = new ClassValue<>() {
        @Override
        protected Object computeValue(Class<?> type) {
            return resolve(type);
        }
    };

    private TracedRuntimeBinding() {
    }

    /**
     * @return the runtime's {@code traced} method for this class, or {@code null} when the class
     * has none and its methods should simply run
     */
    static MethodHandle forClass(Class<?> instrumented) {
        Object binding = BINDINGS.get(instrumented);
        return binding == UNAVAILABLE ? null : (MethodHandle) binding;
    }

    private static Object resolve(Class<?> instrumented) {
        try {
            Class<?> runtime = Class.forName(RUNTIME_CLASS, true, instrumented.getClassLoader());
            MethodType signature = MethodType.methodType(
                    Object.class, Method.class, Object[].class, Callable.class);

            return MethodHandles.publicLookup().findStatic(runtime, RUNTIME_METHOD, signature);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | LinkageError e) {
            // LinkageError covers the Java 21 case: the runtime is compiled for 25 and cannot load.
            LOG.log(Level.WARNING, "Method tracing inactive for " + instrumented.getName()
                    + ": jeffrey-events is missing, older than this agent, or needs Java 25 (" + e + ")");
            return UNAVAILABLE;
        }
    }
}
