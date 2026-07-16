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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyException;

import static org.junit.jupiter.api.Assertions.*;

class GrpcClientErrorsTest {

    private static StatusRuntimeException grpcError(Status status, String description) {
        return status.withDescription(description).asRuntimeException();
    }

    @Nested
    class ToJeffreyExceptionMethod {

        @Test
        void mapsNotFound_toClientErrorWithNotFoundCode() {
            JeffreyException result = GrpcClientErrors.toJeffreyException(
                    grpcError(Status.NOT_FOUND, "Project not found"));

            assertTrue(result.isClientError());
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, result.getCode());
            assertTrue(result.getCode().isNotFound());
            assertTrue(result.getMessage().contains("Project not found"));
        }

        @Test
        void mapsInvalidArgument_toClientError() {
            JeffreyException result = GrpcClientErrors.toJeffreyException(
                    grpcError(Status.INVALID_ARGUMENT, "Missing session id"));

            assertTrue(result.isClientError());
            assertEquals(ErrorCode.INVALID_REQUEST, result.getCode());
        }

        @Test
        void mapsFailedPrecondition_toClientError() {
            JeffreyException result = GrpcClientErrors.toJeffreyException(
                    grpcError(Status.FAILED_PRECONDITION, "Session still recording"));

            assertTrue(result.isClientError());
            assertEquals(ErrorCode.INVALID_REQUEST, result.getCode());
        }

        @Test
        void mapsUnavailable_toRemoteJeffreyUnavailable() {
            JeffreyException result = GrpcClientErrors.toJeffreyException(
                    grpcError(Status.UNAVAILABLE, "Connection refused"));

            assertTrue(result.isInternalError());
            assertEquals(ErrorCode.REMOTE_JEFFREY_UNAVAILABLE, result.getCode());
        }

        @Test
        void mapsDeadlineExceeded_toRemoteJeffreyUnavailable() {
            JeffreyException result = GrpcClientErrors.toJeffreyException(
                    grpcError(Status.DEADLINE_EXCEEDED, "Deadline exceeded"));

            assertTrue(result.isInternalError());
            assertEquals(ErrorCode.REMOTE_JEFFREY_UNAVAILABLE, result.getCode());
        }

        @Test
        void mapsOtherStatuses_toRemoteOperationFailed() {
            JeffreyException result = GrpcClientErrors.toJeffreyException(
                    grpcError(Status.INTERNAL, "Server blew up"));

            assertTrue(result.isInternalError());
            assertEquals(ErrorCode.REMOTE_OPERATION_FAILED, result.getCode());
        }

        @Test
        void keepsOriginalExceptionAsCause() {
            StatusRuntimeException original = grpcError(Status.INTERNAL, "Server blew up");

            JeffreyException result = GrpcClientErrors.toJeffreyException(original);

            assertSame(original, result.getCause());
        }

        @Test
        void fallsBackToGenericDescription_whenStatusHasNone() {
            JeffreyException result = GrpcClientErrors.toJeffreyException(
                    Status.NOT_FOUND.asRuntimeException());

            assertTrue(result.getMessage().contains("Remote hub call failed"));
            assertTrue(result.getMessage().contains("NOT_FOUND"));
        }
    }
}
