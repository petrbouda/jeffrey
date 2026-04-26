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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import pbouda.jeffrey.shared.common.exception.ErrorCode;
import pbouda.jeffrey.shared.common.exception.ErrorResponse;
import pbouda.jeffrey.shared.common.exception.ErrorType;
import pbouda.jeffrey.shared.common.exception.JeffreyException;

/**
 * Maps Jeffrey-specific and standard exceptions to JSON {@link ErrorResponse}
 * payloads with the correct HTTP status code. Picked up by Spring MVC's
 * {@code ExceptionHandlerExceptionResolver} via {@link ControllerAdvice}.
 */
@ControllerAdvice
public class JeffreyExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyExceptionHandler.class);

    @ExceptionHandler(JeffreyException.class)
    public ResponseEntity<ErrorResponse> handleJeffreyException(JeffreyException ex) {
        if (ex.isClientError()) {
            LOG.warn("Handling a client exception: message={}", ex.getMessage());
        } else {
            LOG.error("Handling an internal exception", ex);
        }
        HttpStatus status = switch (ex.getType()) {
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
            case CLIENT -> ex.getCode().isNotFound() ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(new ErrorResponse(ex.getType(), ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        LOG.warn("Handling an IllegalArgumentException: message={}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ErrorType.CLIENT, ErrorCode.INVALID_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        LOG.error("Handling a GenericException: message={}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ErrorType.INTERNAL, ErrorCode.UNKNOWN_ERROR_RESPONSE, ex.getMessage()));
    }
}
