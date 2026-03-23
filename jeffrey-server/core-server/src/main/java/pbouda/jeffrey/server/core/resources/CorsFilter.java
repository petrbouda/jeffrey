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

package pbouda.jeffrey.server.core.resources;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CORS filter that controls cross-origin access to the API.
 *
 * <p>When enabled, all endpoints allow cross-origin requests (for development
 * with a separate frontend dev server). When disabled, no CORS headers are added,
 * so browsers block any cross-origin access to {@code /api/internal/*}.
 */
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = LoggerFactory.getLogger(CorsFilter.class);

    private final boolean enabled;

    public CorsFilter(boolean enabled) {
        this.enabled = enabled;
        LOG.info("CORS filter initialized: enabled={}", enabled);
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (!enabled) {
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            request.abortWith(buildCorsResponse());
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (!enabled) {
            return;
        }

        addCorsHeaders(response);
    }

    private void addCorsHeaders(ContainerResponseContext response) {
        var headers = response.getHeaders();
        headers.putSingle("Access-Control-Allow-Origin", "*");
        headers.putSingle("Access-Control-Allow-Headers", "*");
        headers.putSingle("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        headers.putSingle("Access-Control-Expose-Headers", "Content-Disposition");
    }

    private Response buildCorsResponse() {
        return Response.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Headers", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .build();
    }
}
