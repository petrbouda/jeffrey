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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleMissingResource(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
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
