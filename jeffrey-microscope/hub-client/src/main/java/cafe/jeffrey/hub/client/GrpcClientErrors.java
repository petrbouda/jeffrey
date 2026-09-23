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

package cafe.jeffrey.hub.client;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.shared.common.exception.JeffreyInternalException;

/**
 * Helpers for classifying gRPC status errors on the client side, so callers express intent
 * ({@code if (GrpcClientErrors.isNotFound(e))}) instead of unpacking {@code e.getStatus().getCode()}.
 */
public abstract class GrpcClientErrors {

    private static final String FALLBACK_DESCRIPTION = "Remote hub call failed";

    public static boolean isNotFound(StatusRuntimeException exception) {
        return exception.getStatus().getCode() == Status.Code.NOT_FOUND;
    }

    /**
     * Translates an inbound gRPC failure into the application's error model so it surfaces
     * with a meaningful HTTP status instead of a generic internal error: remote NOT_FOUND
     * and validation failures stay client errors, connectivity failures are reported as the
     * remote hub being unavailable, everything else as a failed remote operation.
     */
    public static JeffreyException toJeffreyException(StatusRuntimeException exception) {
        Status status = exception.getStatus();
        String description = status.getDescription() != null
                ? status.getDescription()
                : FALLBACK_DESCRIPTION;
        String message = "%s (grpc_status=%s)".formatted(description, status.getCode());

        return switch (status.getCode()) {
            case NOT_FOUND ->
                    new JeffreyClientException(ErrorCode.RESOURCE_NOT_FOUND, message, exception);
            case INVALID_ARGUMENT, FAILED_PRECONDITION, OUT_OF_RANGE ->
                    new JeffreyClientException(ErrorCode.INVALID_REQUEST, message, exception);
            case UNAVAILABLE, DEADLINE_EXCEEDED ->
                    new JeffreyInternalException(ErrorCode.HUB_UNAVAILABLE, message, exception);
            default ->
                    new JeffreyInternalException(ErrorCode.REMOTE_OPERATION_FAILED, message, exception);
        };
    }
}
