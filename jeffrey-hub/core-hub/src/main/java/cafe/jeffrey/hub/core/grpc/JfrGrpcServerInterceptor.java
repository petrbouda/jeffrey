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

import cafe.jeffrey.jfr.events.grpc.GrpcServerExchangeEvent;
import cafe.jeffrey.jfr.events.trace.SpanContext;
import cafe.jeffrey.jfr.events.trace.Tracer;
import com.google.protobuf.MessageLite;
import io.grpc.*;

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
                    event.end();
                    if (event.shouldCommit()) {
                        event.commitSpan();
                    }
                    super.close(status, trailers);
                    return null;
                });
            }

            @Override
            public void sendMessage(RespT message) {
                Tracer.reenter(span, () -> {
                    if (message instanceof MessageLite proto) {
                        event.responseSize += proto.getSerializedSize();
                    }
                    super.sendMessage(message);
                    return null;
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
                    return null;
                });
            }

            /** Where a unary handler actually runs, so this is the one that nests the service method. */
            @Override
            public void onHalfClose() {
                Tracer.reenter(span, () -> {
                    super.onHalfClose();
                    return null;
                });
            }

            @Override
            public void onCancel() {
                Tracer.reenter(span, () -> {
                    super.onCancel();
                    return null;
                });
            }

            @Override
            public void onComplete() {
                Tracer.reenter(span, () -> {
                    super.onComplete();
                    return null;
                });
            }

            @Override
            public void onReady() {
                Tracer.reenter(span, () -> {
                    super.onReady();
                    return null;
                });
            }
        };
    }
}
