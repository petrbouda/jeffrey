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

package cafe.jeffrey.jfr.events.grpc.interceptor;

import cafe.jeffrey.jfr.events.grpc.GrpcServerExchangeEvent;
import cafe.jeffrey.jfr.events.trace.SpanContext;
import cafe.jeffrey.jfr.events.trace.Tracer;
import com.google.protobuf.MessageLite;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * gRPC server interceptor that emits {@link GrpcServerExchangeEvent} JFR events
 * for every incoming gRPC call, capturing service/method names, remote peer info,
 * status codes, and request/response sizes.
 * <p>
 * The exchange is also the root of a trace: it is an inbound call, so nothing encloses it. The
 * event carries the trace identity itself rather than a separate span event being emitted for the
 * same interval.
 * <p>
 * <b>Scope of the binding.</b> A gRPC call is not a single block of work — the handler runs from
 * listener callbacks after this method has returned, on threads this interceptor does not control.
 * The span is therefore opened without binding, via {@link Tracer#openSpanOf}, and re-established
 * with {@link Tracer#reenter} around every callback the call arrives in. Work the handler does
 * nests under the exchange as a result, and each re-entry records which thread it ran on, which is
 * what keeps the correlation honest when the call closes on a different thread than it opened on.
 * <p>
 * For a unary call the handler runs inside {@code onHalfClose}, so that is the callback that
 * actually carries the service method; the others are wrapped because a streaming call spreads its
 * work across all of them.
 * <p>
 * Register it once, globally, rather than per service — on Spring gRPC that is a
 * {@code @GlobalServerInterceptor} bean, and on plain gRPC
 * {@code ServerBuilder.intercept(new JfrGrpcServerInterceptor())}.
 */
public class JfrGrpcServerInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        GrpcServerExchangeEvent event = new GrpcServerExchangeEvent();
        if (!event.isEnabled()) {
            return next.startCall(call, headers);
        }

        event.begin();
        // Stamps a fresh trace and span id onto the exchange and keeps the context, because the work
        // this span covers has not started yet and will not arrive on this thread.
        SpanContext span = Tracer.openSpanOf(event);

        MethodDescriptor<ReqT, RespT> methodDescriptor = call.getMethodDescriptor();
        event.service = methodDescriptor.getServiceName();
        event.method = methodDescriptor.getBareMethodName();
        event.authority = call.getAuthority();

        SocketAddress remoteAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        if (remoteAddr instanceof InetSocketAddress inet) {
            event.remoteHost = inet.getHostString();
            event.remotePort = inet.getPort();
        }

        ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                // The event is committed inside the scope rather than after it, so that anything the
                // transport does on the way out still falls within the span it belongs to.
                Tracer.reenter(span, () -> {
                    event.statusCode = status.getCode().name();
                    // A status that carries a cause knows more than its code does: recording it
                    // puts the exception type on the span, the way the client side does.
                    if (status.getCause() != null) {
                        event.failed(status.getCause());
                    }
                    event.end();
                    if (event.shouldCommit()) {
                        event.commitSpan();
                    }
                    super.close(status, trailers);
                });
            }

            @Override
            public void sendMessage(RespT message) {
                Tracer.reenter(span, () -> {
                    if (message instanceof MessageLite proto) {
                        event.responseSize += proto.getSerializedSize();
                    }
                    super.sendMessage(message);
                });
            }
        };

        ServerCall.Listener<ReqT> listener = next.startCall(wrappedCall, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onMessage(ReqT message) {
                Tracer.reenter(span, () -> {
                    if (message instanceof MessageLite proto) {
                        event.requestSize += proto.getSerializedSize();
                    }
                    super.onMessage(message);
                });
            }

            /** Where a unary handler actually runs, so this is the one that nests the service method. */
            @Override
            public void onHalfClose() {
                Tracer.reenter(span, () -> {
                    super.onHalfClose();
                });
            }

            @Override
            public void onCancel() {
                Tracer.reenter(span, () -> {
                    super.onCancel();
                });
            }

            @Override
            public void onComplete() {
                Tracer.reenter(span, () -> {
                    super.onComplete();
                });
            }

            @Override
            public void onReady() {
                Tracer.reenter(span, () -> {
                    super.onReady();
                });
            }
        };
    }
}
