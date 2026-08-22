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

package cafe.jeffrey.jfr.events.spring;

import cafe.jeffrey.jfr.events.servlet.HttpRequestNaming;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Names a request by the Spring MVC handler pattern it matched — {@code /api/users/{id}}, not
 * {@code /api/users/42}.
 * <p>
 * This is the whole reason {@link HttpRequestNaming} exists as an interface: the matched template
 * is knowledge only the routing framework has, and keeping it here leaves
 * {@code jeffrey-events-servlet} free of any web framework.
 * <p>
 * A request that matched no handler — a static asset, a 404 — is named
 * {@link HttpRequestNaming#UNMATCHED_URI} rather than by its raw path. The name becomes the
 * identity of a whole trace type in Jeffrey, so the raw path would produce one "operation" per
 * asset and per mistyped URL, against the stable-and-low-cardinality contract. Such requests are
 * still recorded; they are simply named together.
 */
public class SpringMvcRequestNaming implements HttpRequestNaming {

    @Override
    public String uri(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String matched && !matched.isEmpty()) {
            String contextPath = request.getContextPath();
            return contextPath == null || contextPath.isEmpty() ? matched : contextPath + matched;
        }
        return UNMATCHED_URI;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> pathParams(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof Map<?, ?> map) {
            return (Map<String, String>) map;
        }
        return Map.of();
    }
}
