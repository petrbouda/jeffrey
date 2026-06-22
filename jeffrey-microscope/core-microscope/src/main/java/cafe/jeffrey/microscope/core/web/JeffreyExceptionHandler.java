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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.ErrorResponse;
import cafe.jeffrey.shared.common.exception.ErrorType;
import cafe.jeffrey.shared.common.exception.JeffreyException;

import java.util.Set;

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

    /**
     * A client called an endpoint with an unsupported HTTP method (e.g. a GET against a POST-only route).
     * Respond with a plain 405 + {@code Allow} header and no body: this is a client mistake (logged at
     * debug, not error), and emitting a JSON body would force content negotiation that fails for clients
     * whose {@code Accept} excludes JSON (e.g. an EventSource sending {@code text/event-stream}).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        LOG.debug("Unsupported HTTP method: {}", ex.getMessage());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            builder.allow(supported.toArray(new HttpMethod[0]));
        }
        return builder.build();
    }

    /**
     * Content negotiation produced no writable representation for the client's {@code Accept} header.
     * Respond with a bodyless 406 instead of letting it bubble up as an internal error.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        LOG.debug("No acceptable representation for request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        LOG.error("Handling a GenericException: message={}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ErrorType.INTERNAL, ErrorCode.UNKNOWN_ERROR_RESPONSE, ex.getMessage()));
    }
}
