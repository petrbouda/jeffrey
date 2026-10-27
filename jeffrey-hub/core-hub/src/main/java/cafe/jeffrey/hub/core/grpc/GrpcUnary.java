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

import io.grpc.stub.StreamObserver;

import java.util.function.Supplier;

/**
 * Runs a unary gRPC handler body and completes the response. The body computes and returns the
 * response message; on success this sends {@code onNext}/{@code onCompleted}, and on any exception
 * it maps it to a gRPC status via {@link GrpcExceptions#toStatus(Throwable)} and sends
 * {@code onError}. This removes the repetitive try/catch/onError envelope from every unary service
 * method, keeping the mapping in one place and fully in-process (no Spring interceptor required, so
 * the in-process service tests observe the same behavior).
 *
 * <p>Service methods throw a {@link io.grpc.StatusRuntimeException} (via {@link GrpcExceptions}) for
 * expected errors, or let a domain exception propagate; the central mapper turns it into the right
 * status.
 */
public abstract class GrpcUnary {

    public static <T> void respond(StreamObserver<T> responseObserver, Supplier<T> body) {
        T response;
        try {
            response = body.get();
        } catch (Exception e) {
            responseObserver.onError(GrpcExceptions.toStatus(e));
            return;
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
