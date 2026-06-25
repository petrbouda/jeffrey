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
