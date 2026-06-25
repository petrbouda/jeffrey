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

/**
 * Helpers for classifying gRPC status errors on the client side, so callers express intent
 * ({@code if (GrpcClientErrors.isNotFound(e))}) instead of unpacking {@code e.getStatus().getCode()}.
 */
public abstract class GrpcClientErrors {

    public static boolean isNotFound(StatusRuntimeException exception) {
        return exception.getStatus().getCode() == Status.Code.NOT_FOUND;
    }
}
