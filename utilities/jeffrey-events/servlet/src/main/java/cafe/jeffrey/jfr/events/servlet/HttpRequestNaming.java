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
 * This interface is why {@code jeffrey-events-servlet} needs no web framework: the filter asks for
 * a name, and whoever knows the routing supplies one. {@code jeffrey-events-spring} contributes a
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
