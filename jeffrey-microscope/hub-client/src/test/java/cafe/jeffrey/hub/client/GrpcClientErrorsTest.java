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
            assertEquals(ErrorCode.HUB_UNAVAILABLE, result.getCode());
        }

        @Test
        void mapsDeadlineExceeded_toRemoteJeffreyUnavailable() {
            JeffreyException result = GrpcClientErrors.toJeffreyException(
                    grpcError(Status.DEADLINE_EXCEEDED, "Deadline exceeded"));

            assertTrue(result.isInternalError());
            assertEquals(ErrorCode.HUB_UNAVAILABLE, result.getCode());
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
