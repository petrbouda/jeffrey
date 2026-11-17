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

package cafe.jeffrey.volumescheduler.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

/**
 * Maps every exception to an {@link ApiError}: an unknown job is a 404, an exception Spring MVC
 * raises itself (no route, a method the route does not take, a body it cannot read — every one an
 * {@link ErrorResponse}) keeps the status Spring gave it, and anything else is a 500.
 */
@ControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** The codes a status has a name of its own for; any other 4xx is a bad request. */
    private static final Map<HttpStatus, ApiErrorCode> CODES = Map.of(
            HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND,
            HttpStatus.METHOD_NOT_ALLOWED, ApiErrorCode.METHOD_NOT_ALLOWED);

    @ExceptionHandler(UnknownJobException.class)
    public ResponseEntity<ApiError> unknownJob(UnknownJobException ex) {
        LOG.warn("Request rejected: message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(ApiErrorCode.JOB_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> any(Exception ex) {
        if (ex instanceof ErrorResponse response) {
            HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
            if (status.is4xxClientError()) {
                LOG.warn("Request rejected: status={} message={}", status.value(), ex.getMessage());
                return ResponseEntity.status(status)
                        .body(new ApiError(CODES.getOrDefault(status, ApiErrorCode.BAD_REQUEST), ex.getMessage()));
            }
        }
        LOG.error("Request failed: message={}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(ApiErrorCode.INTERNAL_ERROR, ex.getMessage()));
    }
}
