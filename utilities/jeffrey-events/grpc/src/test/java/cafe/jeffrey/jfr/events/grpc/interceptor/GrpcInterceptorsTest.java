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
import cafe.jeffrey.jfr.events.grpc.GrpcServerExchangeEvent;
import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.jfr.events.test.SpansAssert;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.SpanStatus;
import cafe.jeffrey.jfr.events.trace.Tracer;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Driven over a real in-process gRPC server, the way the repository's other gRPC tests are: the
 * whole difficulty of this instrumentation is that a call arrives in callbacks on threads nobody
 * controls, and only a real server exercises that.
 */
class GrpcInterceptorsTest {

    private static final String SERVICE = "jeffrey.test.EchoService";
    private static final String METHOD = "Echo";
    private static final String FULL_METHOD = SERVICE + "/" + METHOD;
    private static final String FAILING_METHOD = "Fail";
    private static final String FAILING_FULL_METHOD = SERVICE + "/" + FAILING_METHOD;

    private String serverName;
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws IOException {
        serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(echoService())
                .intercept(new JfrGrpcServerInterceptor())
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .intercept(new JfrGrpcClientInterceptor())
                .build();
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Nested
    @DisplayName("The server side")
    class ServerSide {

        @Test
        @DisplayName("an inbound call is the root of its trace, named by service and method")
        void inboundCallIsATraceRoot() throws IOException {
            RecordedEvent event = JfrRecordings.single(GrpcServerExchangeEvent.NAME, () -> echo("hello"));

            assertEquals(FULL_METHOD, event.getString("name"));
            assertEquals(SpanKind.SERVER.name(), event.getString("kind"));
            assertEquals(SpanStatus.OK.name(), event.getString("status"));
            assertEquals(0, event.getLong("parentSpanId"), "an inbound call has nothing above it");
        }

        @Test
        @DisplayName("work the handler does nests under the call, even across its callbacks")
        void handlerWorkNestsUnderTheCall() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(
                    List.of(GrpcServerExchangeEvent.NAME, "jeffrey.TraceSpan"),
                    () -> echo("hello"));

            SpansAssert.assertThat(events)
                    .hasNoUntracedSpans()
                    .hasSpan("echo.handle").nestedUnder(FULL_METHOD);
        }

        @Test
        @DisplayName("a failed call is recorded as an error rather than judged only by its code")
        void failedCallIsRecorded() throws IOException {
            RecordedEvent event = JfrRecordings.single(GrpcServerExchangeEvent.NAME,
                    () -> assertThrows(StatusRuntimeException.class, () -> call(FAILING_FULL_METHOD, "boom")));

            assertEquals(SpanStatus.ERROR.name(), event.getString("status"));
            assertEquals(Status.Code.INTERNAL.name(), event.getString("statusCode"));
            assertEquals(IllegalStateException.class.getName(), event.getString("errorType"),
                    "a status carrying a cause knows more than its code does");
        }
    }

    @Nested
    @DisplayName("The client side")
    class ClientSide {

        @Test
        @DisplayName("an outbound call is a leaf under the span that made it, not a trace of its own")
        void outboundCallNestsUnderTheCaller() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(
                    List.of(GrpcClientExchangeEvent.NAME, "jeffrey.TraceSpan"),
                    () -> Tracer.run("orders.sync", SpanKind.SERVER, () -> echo("hello")));

            SpansAssert.assertThat(events)
                    .hasNoUntracedSpans()
                    .hasSpan(FULL_METHOD)
                    .nestedUnder("orders.sync")
                    .hasKind(SpanKind.CLIENT.name())
                    .hasEventType(GrpcClientExchangeEvent.NAME);
        }

        @Test
        @DisplayName("request and response sizes are recorded from the messages themselves")
        void recordsMessageSizes() throws IOException {
            RecordedEvent event = JfrRecordings.single(GrpcClientExchangeEvent.NAME, () -> echo("hello"));

            assertEquals(SERVICE, event.getString("service"));
            assertEquals(METHOD, event.getString("method"));
            assertEquals(SpanStatus.OK.name(), event.getString("status"));
            assertTrue(event.getLong("requestSize") >= 0);
        }

        @Test
        @DisplayName("a failed call is red on the client too, with the status the server closed with")
        void failedCallIsRecorded() throws IOException {
            RecordedEvent event = JfrRecordings.single(GrpcClientExchangeEvent.NAME,
                    () -> assertThrows(StatusRuntimeException.class, () -> call(FAILING_FULL_METHOD, "boom")));

            assertEquals(SpanStatus.ERROR.name(), event.getString("status"));
            assertEquals(Status.Code.INTERNAL.name(), event.getString("statusCode"));
        }

        @Test
        @DisplayName("outside any span the call still records, as the root of its own trace")
        void outboundCallOutsideASpanIsStillRecorded() throws IOException {
            RecordedEvent event = JfrRecordings.single(GrpcClientExchangeEvent.NAME, () -> echo("hello"));

            assertEquals(0, event.getLong("parentSpanId"));
        }
    }

    private String echo(String message) {
        return call(FULL_METHOD, message);
    }

    private String call(String fullMethod, String message) {
        return ClientCalls.blockingUnaryCall(
                channel, descriptorFor(fullMethod), CallOptions.DEFAULT, message);
    }

    /**
     * A hand-rolled string-over-the-wire service, so the test needs no generated protobuf types.
     */
    private ServerServiceDefinition echoService() {
        ServerCallHandler<String, String> echo = ServerCalls.asyncUnaryCall((request, responder) -> {
            // Runs inside the server span; recorded so the test can assert the nesting.
            String reply = Tracer.call("echo.handle", SpanKind.INTERNAL, () -> request.toUpperCase());
            responder.onNext(reply);
            responder.onCompleted();
        });
        ServerCallHandler<String, String> fail = ServerCalls.asyncUnaryCall((request, responder) ->
                responder.onError(Status.INTERNAL.withDescription("boom")
                        .withCause(new IllegalStateException("boom"))
                        .asRuntimeException()));

        return ServerServiceDefinition.builder(SERVICE)
                .addMethod(descriptorFor(FULL_METHOD), echo)
                .addMethod(descriptorFor(FAILING_FULL_METHOD), fail)
                .build();
    }

    private static MethodDescriptor<String, String> descriptorFor(String fullMethod) {
        return MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(fullMethod)
                .setRequestMarshaller(StringMarshaller.INSTANCE)
                .setResponseMarshaller(StringMarshaller.INSTANCE)
                .build();
    }

    private enum StringMarshaller implements MethodDescriptor.Marshaller<String> {

        INSTANCE;

        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
