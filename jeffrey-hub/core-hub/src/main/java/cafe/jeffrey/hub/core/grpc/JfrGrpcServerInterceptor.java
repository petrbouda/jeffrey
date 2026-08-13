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
 * listener callbacks after this method has returned, on a thread this interceptor does not control.
 * The published context therefore covers only the synchronous setup below, so work performed later
 * in the call is <em>not</em> nested under this span. The call still appears in the trace list with
 * its own identity, which is what makes it addressable at all; nesting the handler's work needs a
 * way to re-enter an existing span that the tracing API does not currently offer.
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
        // Stamps a fresh trace and span id onto the exchange; see the note on the class javadoc
        // about how far the published context reaches.
        Tracer.inSpanOf(event, () -> null);

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
                event.status = status.getCode().name();
                event.end();
                if (event.shouldCommit()) {
                    event.commit();
                }
                super.close(status, trailers);
            }

            @Override
            public void sendMessage(RespT message) {
                if (message instanceof MessageLite proto) {
                    event.responseSize += proto.getSerializedSize();
                }
                super.sendMessage(message);
            }
        };

        ServerCall.Listener<ReqT> listener = next.startCall(wrappedCall, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onMessage(ReqT message) {
                if (message instanceof MessageLite proto) {
                    event.requestSize += proto.getSerializedSize();
                }
                super.onMessage(message);
            }
        };
    }
}
