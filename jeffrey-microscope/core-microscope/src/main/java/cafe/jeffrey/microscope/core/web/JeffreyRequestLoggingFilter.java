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
