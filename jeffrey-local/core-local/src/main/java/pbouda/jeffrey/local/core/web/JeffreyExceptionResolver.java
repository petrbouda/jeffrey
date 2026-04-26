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

package pbouda.jeffrey.local.core.web;

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
import pbouda.jeffrey.shared.common.exception.JeffreyException;

import java.io.IOException;

/**
 * Maps Jeffrey-specific and standard exceptions to JSON {@link ErrorResponse}
 * payloads with the correct HTTP status code. Replaces the JAX-RS
 * {@code ExceptionMappers} that used to live in this package.
 */
public class JeffreyExceptionResolver implements HandlerExceptionResolver {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyExceptionResolver.class);

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        try {
            switch (ex) {
                case JeffreyException je -> handleJeffreyException(response, je);
                case IllegalArgumentException iae -> handleIllegalArgument(response, iae);
                default -> handleGeneric(response, ex);
            }
        } catch (IOException ioe) {
            LOG.warn("Failed to write error response: error={}", ioe.getMessage());
            return null;
        }
        return new ModelAndView();
    }

    private static void handleJeffreyException(HttpServletResponse response, JeffreyException ex) throws IOException {
        if (ex.isClientError()) {
            LOG.warn("Handling a client exception: message={}", ex.getMessage());
        } else {
            LOG.error("Handling an internal exception", ex);
        }
        HttpStatus status = switch (ex.getType()) {
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
            case CLIENT -> ex.getCode().isNotFound() ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        };
        writeJson(response, status, new ErrorResponse(ex.getType(), ex.getCode(), ex.getMessage()));
    }

    private static void handleIllegalArgument(HttpServletResponse response, IllegalArgumentException ex) throws IOException {
        LOG.warn("Handling an IllegalArgumentException: message={}", ex.getMessage(), ex);
        writeJson(
                response,
                HttpStatus.BAD_REQUEST,
                new ErrorResponse(ErrorType.CLIENT, ErrorCode.INVALID_REQUEST, ex.getMessage()));
    }

    private static void handleGeneric(HttpServletResponse response, Exception ex) throws IOException {
        LOG.error("Handling a GenericException: message={}", ex.getMessage(), ex);
        writeJson(
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                new ErrorResponse(ErrorType.INTERNAL, ErrorCode.UNKNOWN_ERROR_RESPONSE, ex.getMessage()));
    }

    private static void writeJson(HttpServletResponse response, HttpStatus status, ErrorResponse body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(Json.toString(body));
    }
}
