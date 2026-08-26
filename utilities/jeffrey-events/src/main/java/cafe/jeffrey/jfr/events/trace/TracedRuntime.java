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
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs a {@link Traced} method as a span. The Jeffrey agent calls this; applications do not.
 * <p>
 * <b>This class is the contract between the agent and the library, and its one public method is
 * frozen.</b> The agent is compiled against neither this class nor {@link Traced} — it cannot be,
 * since it loads from the system class path while this loads from the application's, and it
 * targets an older Java release. It looks this method up reflectively, by name and signature, so
 * changing either silently disables tracing for every application running an older agent. Add
 * behaviour by reading more of the annotation here, never by changing the signature.
 * <p>
 * Everything a span means lives on this side of that boundary — naming, attributes, the failure
 * verdict — so it is versioned with the library and an application picks up improvements by
 * upgrading the dependency, not the agent.
 */
public final class TracedRuntime {

    /**
     * Longer values are truncated: an argument is a fact about a call, not a payload, and one
     * oversized {@code toString()} must not bloat an entire recording.
     */
    private static final int MAX_CAPTURED_VALUE_LENGTH = 256;

    private static final char KEY_VALUE_SEPARATOR = '=';
    private static final char PACKAGE_SEPARATOR = '.';
    private static final char NESTED_CLASS_SEPARATOR = '$';
    private static final String NAME_SEPARATOR = ".";

    /**
     * Keyed by declaring class so an entry dies with the class that owns it, which matters in a
     * container that loads and discards applications.
     */
    private static final ClassValue<Map<Method, TracedMetadata>> METADATA = new ClassValue<>() {
        @Override
        protected Map<Method, TracedMetadata> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private TracedRuntime() {
    }

    /**
     * Runs {@code body} as the span described by the method's {@link Traced} annotation, and
     * returns whatever it returned.
     * <p>
     * Anything the body throws propagates unchanged — the same instance, checked or not — after
     * being recorded as the span's failure.
     *
     * @param method    the annotated method being called
     * @param arguments the values it was called with, in declaration order
     * @param body      the method's own work
     * @return the body's result, {@code null} for a void method
     */
    public static Object traced(Method method, Object[] arguments, Callable<Object> body) throws Throwable {
        TracedMetadata metadata = metadataOf(method);
        if (metadata == null) {
            // Not annotated after all: run the method and stay out of the way.
            return body.call();
        }

        TraceSpanEvent event = new TraceSpanEvent();
        if (!event.isEnabled()) {
            // Nothing is recording, so the annotation costs one allocation and a branch.
            return body.call();
        }

        event.begin();
        try {
            return Tracer.inSpanOf(event, body::call);
        } catch (Throwable failure) {
            event.failed(failure);
            throw failure;
        } finally {
            event.end();
            if (event.shouldCommit()) {
                event.name = metadata.name();
                event.kind = metadata.kind();
                event.attributes = metadata.attributes(arguments);
                // inSpanOf already stamped the ids, so this commits the span it opened.
                event.commitSpan();
            }
        }
    }

    private static TracedMetadata metadataOf(Method method) {
        Map<Method, TracedMetadata> perClass = METADATA.get(method.getDeclaringClass());
        TracedMetadata cached = perClass.get(method);
        if (cached != null) {
            return cached;
        }

        Traced traced = method.getAnnotation(Traced.class);
        if (traced == null) {
            return null;
        }

        TracedMetadata described = describe(traced, method);
        perClass.put(method, described);
        return described;
    }

    private static TracedMetadata describe(Traced traced, Method method) {
        List<Attribute> staticAttributes = parseAttributes(traced.args());
        List<CapturedParameter> captured = selectParameters(traced, method);
        // An unchanging attribute set is rendered once here rather than on every call.
        String prebuilt = captured.isEmpty() ? render(staticAttributes) : null;

        return new TracedMetadata(
                resolveName(traced, method), traced.kind().name(), staticAttributes, captured, prebuilt);
    }

    /**
     * The declared name, or {@code OrderService.checkout} derived from the method itself. The
     * package is dropped because it lengthens every recorded name without distinguishing anything,
     * and so is any enclosing class, so a nested type reads the same as a top-level one.
     */
    private static String resolveName(Traced traced, Method method) {
        if (!traced.name().isEmpty()) {
            return traced.name();
        }

        String qualified = method.getDeclaringClass().getName();
        int lastSeparator = Math.max(
                qualified.lastIndexOf(PACKAGE_SEPARATOR), qualified.lastIndexOf(NESTED_CLASS_SEPARATOR));
        String simpleName = qualified.substring(lastSeparator + 1);

        return simpleName + NAME_SEPARATOR + method.getName();
    }

    /**
     * Which arguments to record: the ones named, or all of them when capture is on and nothing is
     * named, or none. Naming even one parameter is itself the request to capture, so
     * {@code captureMethodArgs} does not also have to be set.
     */
    private static List<CapturedParameter> selectParameters(Traced traced, Method method) {
        List<String> included = List.of(traced.includeMethodArgs());
        if (included.isEmpty() && !traced.captureMethodArgs()) {
            return List.of();
        }

        Parameter[] parameters = method.getParameters();
        List<CapturedParameter> selected = new ArrayList<>(parameters.length);
        for (int index = 0; index < parameters.length; index++) {
            String parameterName = parameters[index].getName();
            // A name matching nothing is ignored: usually a class compiled without -parameters,
            // where the real names are arg0, arg1, and so on.
            if (included.isEmpty() || included.contains(parameterName)) {
                selected.add(new CapturedParameter(index, parameterName));
            }
        }

        return List.copyOf(selected);
    }

    private static List<Attribute> parseAttributes(String[] declared) {
        List<Attribute> parsed = new ArrayList<>(declared.length);
        for (String entry : declared) {
            int separator = entry.indexOf(KEY_VALUE_SEPARATOR);
            if (separator > 0) {
                parsed.add(new Attribute(entry.substring(0, separator).trim(), entry.substring(separator + 1).trim()));
            }
        }

        return List.copyOf(parsed);
    }

    private static String render(List<Attribute> attributes) {
        if (attributes.isEmpty()) {
            // An absent field reads better than an empty object in the dashboard.
            return null;
        }

        EventAttributes rendered = EventAttributes.create();
        for (Attribute attribute : attributes) {
            rendered.put(attribute.key(), attribute.value());
        }

        return rendered.json();
    }

    /** Everything about a traced method that does not change between calls. */
    private record TracedMetadata(
            String name,
            String kind,
            List<Attribute> staticAttributes,
            List<CapturedParameter> capturedParameters,
            String prebuiltAttributes) {

        String attributes(Object[] arguments) {
            if (capturedParameters.isEmpty()) {
                return prebuiltAttributes;
            }

            EventAttributes rendered = EventAttributes.create();
            for (Attribute attribute : staticAttributes) {
                rendered.put(attribute.key(), attribute.value());
            }
            for (CapturedParameter parameter : capturedParameters) {
                // Defensive on length: a woven call always passes every argument, but this class is
                // callable by anyone.
                Object value = parameter.index() < arguments.length ? arguments[parameter.index()] : null;
                AttributeValues.put(rendered, parameter.name(), value, MAX_CAPTURED_VALUE_LENGTH);
            }

            return rendered.json();
        }
    }

    private record Attribute(String key, String value) {
    }

    private record CapturedParameter(int index, String name) {
    }
}
