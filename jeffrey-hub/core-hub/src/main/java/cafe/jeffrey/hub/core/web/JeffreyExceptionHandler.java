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

package cafe.jeffrey.hub.core.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.ErrorResponse;
import cafe.jeffrey.shared.common.exception.ErrorType;

/**
 * Maps exceptions to a JSON {@link ErrorResponse}: a status a controller chose deliberately is
 * preserved, anything else becomes HTTP 500.
 * Picked up by Spring MVC's {@code ExceptionHandlerExceptionResolver} via
 * {@link ControllerAdvice}.
 */
@ControllerAdvice
public class JeffreyExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyExceptionHandler.class);

    /**
     * Preserves a status a controller chose deliberately. Without this the catch-all below would
     * turn every {@code ResponseStatusException} into a 500 and tell the client the server broke
     * when it was the request that was wrong.
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleStatus(ErrorResponseException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        LOG.warn("Request rejected: status={} message={}", status.value(), ex.getMessage());
        return ResponseEntity.status(status)
                .body(new ErrorResponse(
                        status.is4xxClientError() ? ErrorType.CLIENT : ErrorType.INTERNAL,
                        ErrorCode.UNKNOWN_ERROR_RESPONSE,
                        ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        LOG.error("Handling a GenericException: message={}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ErrorType.INTERNAL, ErrorCode.UNKNOWN_ERROR_RESPONSE, ex.getMessage()));
    }
}
