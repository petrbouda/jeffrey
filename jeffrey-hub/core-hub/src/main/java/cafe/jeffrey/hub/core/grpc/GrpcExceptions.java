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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.Status;
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

    public static StatusRuntimeException internal(Throwable cause) {
        return Status.INTERNAL.withDescription(cause.getMessage()).asRuntimeException();
    }

    /**
     * Maps an exception thrown by a gRPC service method to a {@link StatusRuntimeException}.
     * Exceptions that already carry a gRPC status pass through; domain validation exceptions map to
     * their standard status; anything else is logged and reported as {@code INTERNAL}. The checked
     * {@code StatusException} has no arm because a {@code Supplier} cannot throw it.
     */
    public static StatusRuntimeException toStatus(Throwable exception) {
        return switch (exception) {
            case StatusRuntimeException e -> e;
            case WorkspaceAlreadyExistsException e ->
                    Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException();
            case IllegalArgumentException e ->
                    Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
            // What the storage says when a project has no repository row yet — a state the
            // reconciler expects, so it is the caller's timing, not this server's failure
            case IllegalStateException e ->
                    Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException();
            default -> {
                LOG.error("Unhandled gRPC service exception", exception);
                yield Status.INTERNAL.withDescription(exception.getMessage()).asRuntimeException();
            }
        };
    }
}
