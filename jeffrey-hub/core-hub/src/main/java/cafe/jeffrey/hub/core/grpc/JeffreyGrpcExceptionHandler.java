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

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceAlreadyExistsException;

/**
 * Central server-side exception mapping for every hub gRPC service. Spring gRPC's auto-configured
 * exception handling routes each exception thrown from a unary service method through this handler
 * (via {@code GrpcExceptionHandlerInterceptor}), so the services themselves stay free of the
 * repetitive {@code try/catch/onError} envelope: they simply throw a {@link StatusRuntimeException}
 * (via {@link GrpcExceptions}) for expected errors or let a domain exception propagate.
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>{@link StatusException} / {@link StatusRuntimeException} — already carry a gRPC status, passed through unchanged</li>
 *   <li>{@link WorkspaceAlreadyExistsException} — {@code ALREADY_EXISTS}</li>
 *   <li>{@link IllegalArgumentException} — {@code INVALID_ARGUMENT} (domain validation uses it by convention)</li>
 *   <li>anything else — logged and mapped to {@code INTERNAL}</li>
 * </ul>
 */
public class JeffreyGrpcExceptionHandler implements GrpcExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyGrpcExceptionHandler.class);

    @Override
    public StatusException handleException(Throwable exception) {
        return switch (exception) {
            case StatusException e -> e;
            case StatusRuntimeException e -> toStatusException(e);
            case WorkspaceAlreadyExistsException e ->
                    Status.ALREADY_EXISTS.withDescription(e.getMessage()).asException();
            case IllegalArgumentException e ->
                    Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asException();
            default -> {
                LOG.error("Unhandled gRPC service exception", exception);
                yield Status.INTERNAL.withDescription(exception.getMessage()).asException();
            }
        };
    }

    private static StatusException toStatusException(StatusRuntimeException exception) {
        Metadata trailers = exception.getTrailers();
        if (trailers != null) {
            return exception.getStatus().asException(trailers);
        }
        return exception.getStatus().asException();
    }
}
