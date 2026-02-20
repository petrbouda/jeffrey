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

package pbouda.jeffrey.platform.resources;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.UUID;

@Provider
public class RequestLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final String MDC_REQUEST_ID = "requestId";
    private static final String REQUEST_START_TIME = "jeffrey.requestStartTime";

    @Override
    public void filter(ContainerRequestContext request) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_REQUEST_ID, requestId);

        request.setProperty(REQUEST_START_TIME, System.nanoTime());

        LOG.debug("Request started: method={} path={}",
                request.getMethod(),
                request.getUriInfo().getPath());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        try {
            Long startTime = (Long) request.getProperty(REQUEST_START_TIME);
            long durationMs = startTime != null ? Duration.ofNanos(System.nanoTime() - startTime).toMillis() : -1;

            LOG.debug("Request completed: method={} path={} status={} durationMs={}",
                    request.getMethod(),
                    request.getUriInfo().getPath(),
                    response.getStatus(),
                    durationMs);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}
