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

package cafe.jeffrey.jfr.events.servlet;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * Decides what a request is <em>called</em> — the single decision that keeps an HTTP span's name
 * low-cardinality, and the one thing no servlet container can answer on its own.
 * <p>
 * The span name is derived from the recorded {@code uri}, and every distinct name enters the JFR
 * per-chunk constant pool. Recording the raw path produces one "operation" per entity id, which
 * inflates the recording and turns Jeffrey's HTTP dashboard into a list of individual requests. The
 * answer is the routing framework's matched <em>template</em> — {@code /api/users/{id}} — which
 * only the framework knows.
 * <p>
 * This interface is why {@code jeffrey-tracing-servlet} needs no web framework: the filter asks for
 * a name, and whoever knows the routing supplies one. {@code jeffrey-tracing-spring} contributes a
 * Spring MVC implementation reading the matched handler pattern.
 */
public interface HttpRequestNaming {

    /**
     * Stands in for a request that matched no route — a static asset, a 404. Deliberately a fixed
     * label rather than the raw path, which would produce one operation name per mistyped URL.
     */
    String UNMATCHED_URI = "<unmatched>";

    /**
     * @return a stable, low-cardinality name for the endpoint this request hit
     */
    String uri(HttpServletRequest request);

    /**
     * @return the route's template variables, e.g. {@code {id=42}}; empty when the naming strategy
     * cannot resolve them or the route has none
     */
    default Map<String, String> pathParams(HttpServletRequest request) {
        return Map.of();
    }

    /**
     * Names a request by the pattern its servlet was mapped with, e.g. {@code /api/*} — the best a
     * container can do without a routing framework, and already low-cardinality because a mapping
     * is declared, not derived from the request.
     */
    static HttpRequestNaming servletMapping() {
        return request -> {
            if (request.getHttpServletMapping() == null) {
                return UNMATCHED_URI;
            }
            String pattern = request.getHttpServletMapping().getPattern();
            return pattern == null || pattern.isEmpty() ? UNMATCHED_URI : pattern;
        };
    }
}
