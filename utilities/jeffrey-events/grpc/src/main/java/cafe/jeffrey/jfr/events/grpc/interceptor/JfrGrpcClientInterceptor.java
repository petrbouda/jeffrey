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

package cafe.jeffrey.jfr.events.grpc.interceptor;

import cafe.jeffrey.jfr.events.grpc.GrpcClientExchangeEvent;
import cafe.jeffrey.jfr.events.trace.SpanContext;
import cafe.jeffrey.jfr.events.trace.Tracer;
import com.google.protobuf.MessageLite;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

/**
 * Emits a {@link GrpcClientExchangeEvent} for every outbound gRPC call.
 * <p>
 * The mirror of {@link JfrGrpcServerInterceptor}, and different from it in exactly one way that
 * matters: an outbound call is a <b>leaf</b>, not a trace root. The span is opened on the calling
 * thread, so {@link Tracer#openSpanOf} makes it a child of whatever span is in progress there —
 * the request being served, typically — instead of starting a trace of its own. The downstream
 * work happens in another process this recording cannot see, so nothing nests inside it.
 * <p>
 * Like the server side, a gRPC call is not a single block of work: the response arrives on
 * transport threads long after {@code interceptCall} returned. The span is therefore opened
 * without binding and re-established with {@link Tracer#reenter} around each callback, which also
 * records which thread the call actually completed on.
 *
 * <pre>{@code
 * ManagedChannel channel = NettyChannelBuilder.forAddress(host, port)
 *         .intercept(new JfrGrpcClientInterceptor())
 *         .build();
 * }</pre>
 */
public class JfrGrpcClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        GrpcClientExchangeEvent event = new GrpcClientExchangeEvent();
        if (!event.isEnabled()) {
            return next.newCall(method, callOptions);
        }

        event.begin();
        // Opened here, on the calling thread, because this is where the enclosing span is bound -
        // by the time the response arrives there is nothing left to be a child of.
        SpanContext span = Tracer.openSpanOf(event);
        event.service = method.getServiceName();
        event.method = method.getBareMethodName();
        event.authority = next.authority();

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new TracingListener<>(responseListener, event, span), headers);
            }

            @Override
            public void sendMessage(ReqT message) {
                if (message instanceof MessageLite proto) {
                    event.requestSize += proto.getSerializedSize();
                }
                super.sendMessage(message);
            }
        };
    }

    /**
     * Re-enters the call's span for every response callback, and commits the exchange when the call
     * closes — which is the one callback guaranteed to arrive, for a success and a failure alike.
     */
    private static final class TracingListener<RespT>
            extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {

        private final GrpcClientExchangeEvent event;
        private final SpanContext span;

        private TracingListener(ClientCall.Listener<RespT> delegate, GrpcClientExchangeEvent event, SpanContext span) {
            super(delegate);
            this.event = event;
            this.span = span;
        }

        @Override
        public void onMessage(RespT message) {
            Tracer.reenter(span, () -> {
                if (message instanceof MessageLite proto) {
                    event.responseSize += proto.getSerializedSize();
                }
                super.onMessage(message);
            });
        }

        @Override
        public void onHeaders(Metadata headers) {
            Tracer.reenter(span, () -> super.onHeaders(headers));
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            Tracer.reenter(span, () -> {
                event.statusCode = status.getCode().name();
                if (status.getCause() != null) {
                    event.failed(status.getCause());
                }
                event.end();
                if (event.shouldCommit()) {
                    event.commitSpan();
                }
                super.onClose(status, trailers);
            });
        }
    }
}
