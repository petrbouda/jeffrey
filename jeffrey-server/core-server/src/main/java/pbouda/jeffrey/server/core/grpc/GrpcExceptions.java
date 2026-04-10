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

package pbouda.jeffrey.server.core.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/**
 * Factory methods for creating gRPC {@link StatusRuntimeException} instances
 * with standard status codes and descriptions.
 */
public abstract class GrpcExceptions {

    public static StatusRuntimeException notFound(String description) {
        return Status.NOT_FOUND.withDescription(description).asRuntimeException();
    }

    public static StatusRuntimeException invalidArgument(String description) {
        return Status.INVALID_ARGUMENT.withDescription(description).asRuntimeException();
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
}
