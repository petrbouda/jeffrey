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

package cafe.jeffrey.microscope.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.measure.Measuring;

import java.io.IOException;
import java.time.Duration;

/**
 * Adds a short request id to the SLF4J MDC and emits debug log lines for the
 * request and response. Replaces the JAX-RS request logging filter.
 */
public class JeffreyRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyRequestLoggingFilter.class);
    private static final String MDC_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        MDC.put(MDC_REQUEST_ID, IDGenerator.generate());

        LOG.debug("HTTP_REQ: method={} path={}", request.getMethod(), request.getRequestURI());
        try {
            Duration elapsed = Measuring.r(() -> {
                try {
                    filterChain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    throw new RuntimeException(e);
                }
            });
            LOG.debug("HTTP_RESP: method={} path={} status={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsed.toMillis());
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}
