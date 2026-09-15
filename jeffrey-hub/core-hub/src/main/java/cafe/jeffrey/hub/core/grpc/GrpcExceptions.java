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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceAlreadyExistsException;

/**
 * Factory methods for creating gRPC {@link StatusRuntimeException} instances with standard status
 * codes and descriptions, plus the central {@link #toStatus(Throwable)} mapper used by
 * {@link GrpcUnary} to turn service exceptions into a gRPC status in one place.
 */
public abstract class GrpcExceptions {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcExceptions.class);

    public static StatusRuntimeException notFound(String description) {
        return Status.NOT_FOUND.withDescription(description).asRuntimeException();
    }

    public static StatusRuntimeException invalidArgument(String description) {
        return Status.INVALID_ARGUMENT.withDescription(description).asRuntimeException();
    }

    public static StatusRuntimeException failedPrecondition(String description) {
        return Status.FAILED_PRECONDITION.withDescription(description).asRuntimeException();
    }

    public static StatusRuntimeException unavailable(String description) {
        return Status.UNAVAILABLE.withDescription(description).asRuntimeException();
    }

    public static StatusRuntimeException internal(String description) {
        return Status.INTERNAL.withDescription(description).asRuntimeException();
    }

    public static StatusRuntimeException internal(Throwable cause) {
        return Status.INTERNAL.withDescription(cause.getMessage()).asRuntimeException();
    }

    /**
     * Maps an exception thrown by a gRPC service method to a {@link StatusRuntimeException}.
     * Exceptions that already carry a gRPC status pass through; domain validation exceptions map to
     * their standard status; anything else is logged and reported as {@code INTERNAL}.
     */
    public static StatusRuntimeException toStatus(Throwable exception) {
        return switch (exception) {
            case StatusRuntimeException e -> e;
            case StatusException e -> e.getStatus().asRuntimeException();
            case WorkspaceAlreadyExistsException e ->
                    Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException();
            case IllegalArgumentException e ->
                    Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
            default -> {
                LOG.error("Unhandled gRPC service exception", exception);
                yield Status.INTERNAL.withDescription(exception.getMessage()).asRuntimeException();
            }
        };
    }
}
