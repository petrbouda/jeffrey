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

package pbouda.jeffrey.server.core.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.exception.ErrorCode;
import pbouda.jeffrey.shared.common.exception.ErrorResponse;
import pbouda.jeffrey.shared.common.exception.ErrorType;

import java.io.IOException;

/**
 * Maps any uncaught exception to a JSON {@link ErrorResponse} with HTTP 500.
 * Replaces the JAX-RS {@code GenericExceptionMapper} that used to live here.
 */
public class JeffreyExceptionResolver implements HandlerExceptionResolver {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyExceptionResolver.class);

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        LOG.error("Handling a GenericException: message={}", ex.getMessage(), ex);
        try {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(Json.toString(
                    new ErrorResponse(ErrorType.INTERNAL, ErrorCode.UNKNOWN_ERROR_RESPONSE, ex.getMessage())));
        } catch (IOException ioe) {
            LOG.warn("Failed to write error response: error={}", ioe.getMessage());
            return null;
        }
        return new ModelAndView();
    }
}
