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

package cafe.jeffrey.server.core.grpc;

import cafe.jeffrey.jfr.events.grpc.GrpcServerExchangeEvent;
import com.google.protobuf.MessageLite;
import io.grpc.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * gRPC server interceptor that emits {@link GrpcServerExchangeEvent} JFR events
 * for every incoming gRPC call, capturing service/method names, remote peer info,
 * status codes, and request/response sizes.
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
