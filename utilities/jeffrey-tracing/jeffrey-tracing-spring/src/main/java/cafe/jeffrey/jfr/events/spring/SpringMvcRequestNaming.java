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
 * {@code jeffrey-tracing-servlet} free of any web framework.
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
