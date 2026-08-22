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

package cafe.jeffrey.jfr.events.trace;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Stands in for the library's tracing runtime, with the exact signature the agent looks up. It
 * records what it was handed and runs the body, so a test can assert that the woven method reached
 * the bridge with the right method, the right arguments and a working body — the whole of what the
 * agent is responsible for.
 *
 * @see Traced
 */
public final class TracedRuntime {

    private static final List<Invocation> INVOCATIONS = new ArrayList<>();

    /** What one call through the bridge looked like. */
    public record Invocation(Method method, Object[] arguments) {
    }

    private TracedRuntime() {
    }

    public static Object traced(Method method, Object[] arguments, Callable<Object> body) throws Throwable {
        synchronized (INVOCATIONS) {
            INVOCATIONS.add(new Invocation(method, arguments));
        }
        return body.call();
    }

    public static List<Invocation> invocations() {
        synchronized (INVOCATIONS) {
            return List.copyOf(INVOCATIONS);
        }
    }

    public static void reset() {
        synchronized (INVOCATIONS) {
            INVOCATIONS.clear();
        }
    }
}
