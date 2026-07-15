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
                    new JeffreyInternalException(ErrorCode.REMOTE_JEFFREY_UNAVAILABLE, message, exception);
            default ->
                    new JeffreyInternalException(ErrorCode.REMOTE_OPERATION_FAILED, message, exception);
        };
    }
}
