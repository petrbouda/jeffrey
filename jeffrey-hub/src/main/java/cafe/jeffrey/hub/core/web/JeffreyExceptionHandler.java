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

package cafe.jeffrey.hub.core.web;

import cafe.jeffrey.hub.core.scheduler.ManualJobRunner.ManualRunNotSupportedException;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.ErrorResponse;
import cafe.jeffrey.shared.common.exception.ErrorType;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps exceptions to the API's JSON {@link ErrorResponse}: a client error keeps its code (404 for
 * a missing job, 400 otherwise), a job without a manual run is a 409, a method the route does not
 * take a 405, a status a controller chose deliberately is preserved, and anything else is a 500.
 * Picked up by Spring MVC's {@code ExceptionHandlerExceptionResolver} via
 * {@link ControllerAdvice}.
 */
@ControllerAdvice
public class JeffreyExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleMissingResource(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(JeffreyException.class)
    public ResponseEntity<ErrorResponse> handleJeffreyException(JeffreyException ex) {
        if (ex.isClientError()) {
            LOG.warn("Request rejected: code={} message={}", ex.getCode(), ex.getMessage());
        } else {
            LOG.error("Handling an internal exception: code={}", ex.getCode(), ex);
        }
        HttpStatus status = switch (ex.getType()) {
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
            case CLIENT -> ex.getCode().isNotFound() ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(new ErrorResponse(ex.getType(), ex.getCode(), ex.getMessage()));
    }

    /** The job exists but offers no manual run: the request conflicts with what the job is. */
    @ExceptionHandler(ManualRunNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleManualRunNotSupported(ManualRunNotSupportedException ex) {
        LOG.warn("Request rejected: message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ErrorType.CLIENT, ErrorCode.INVALID_REQUEST, ex.getMessage()));
    }

    /** {@code GET …/run} or {@code POST /api/v1/jobs}: the route exists, the method does not. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(ErrorType.CLIENT, ErrorCode.INVALID_REQUEST, ex.getMessage()));
    }

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
