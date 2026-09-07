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

package cafe.jeffrey.ide.plugin.idea;

import cafe.jeffrey.ide.plugin.idea.settings.JeffreySettings;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.ide.HttpRequestHandler;
import org.jetbrains.ide.RestService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The dispatch rules in front of the handlers: which endpoint answers which verb, what a body the
 * plugin cannot read comes back as, and that a disabled IDE says nothing at all.
 *
 * <p>Driven through {@code execute} over a netty {@link EmbeddedChannel} rather than over a real
 * port, so what is under test is the routing table and not the built-in server. The platform fixture
 * is here for one reason: {@code execute} asks the application for {@link JeffreySettings}.
 */
public class JeffreyMicroscopeServiceTest extends BasePlatformTestCase {

    private static final String ENDPOINT_PREFIX = RestService.PREFIX + "/jeffrey/";

    private static final String PING = "ping";
    private static final String INSTANCE = "instance";
    private static final String NAVIGATE = "navigate";
    private static final String RESOLVE = "resolve";
    private static final String HAS = "has";
    private static final String SOURCE = "source";

    private static final String UNKNOWN_ENDPOINT_MESSAGE = "Unknown Jeffrey endpoint";

    private static final String EMPTY_BODY = "";
    private static final String BROKEN_BODY = "{\"className\": ";
    private static final String NOT_AN_OBJECT_BODY = "\"className\"";

    private final JeffreyMicroscopeService service = new JeffreyMicroscopeService();

    @Override
    protected void tearDown() throws Exception {
        try {
            JeffreySettings.getInstance().setEnabled(true);
        } finally {
            super.tearDown();
        }
    }

    public void testAnswersPingWithTheProtocolVersion() throws IOException {
        Exchange exchange = call(HttpMethod.GET, PING, EMPTY_BODY);

        assertNull(exchange.text());
        assertEquals(HttpResponseStatus.OK, exchange.status());
        assertTrue(exchange.body(), exchange.body()
                .contains("\"protocolVersion\":" + JeffreyMicroscopeService.PROTOCOL_VERSION));
    }

    /**
     * The platform asks {@code isMethodSupported} before the request has a path, so the per-endpoint
     * verb is checked here or nowhere. A GET on {@code navigate} used to reach the body parser with
     * an empty string and come back as a 500 — a malformed request reported as a broken plugin.
     */
    public void testAWriteEndpointRefusesAGet() throws IOException {
        assertEquals(HttpResponseStatus.METHOD_NOT_ALLOWED, call(HttpMethod.GET, NAVIGATE, EMPTY_BODY).status());
        assertEquals(HttpResponseStatus.METHOD_NOT_ALLOWED, call(HttpMethod.GET, RESOLVE, EMPTY_BODY).status());
    }

    public void testAReadEndpointRefusesAPost() throws IOException {
        assertEquals(HttpResponseStatus.METHOD_NOT_ALLOWED, call(HttpMethod.POST, PING, EMPTY_BODY).status());
        assertEquals(HttpResponseStatus.METHOD_NOT_ALLOWED, call(HttpMethod.POST, INSTANCE, EMPTY_BODY).status());
        assertEquals(HttpResponseStatus.METHOD_NOT_ALLOWED, call(HttpMethod.POST, HAS, EMPTY_BODY).status());
        assertEquals(HttpResponseStatus.METHOD_NOT_ALLOWED, call(HttpMethod.POST, SOURCE, EMPTY_BODY).status());
    }

    /** A path with no entry in the table is an error the caller reads, not a 405 about the verb. */
    public void testAnUnknownEndpointIsNotAMethodProblem() throws IOException {
        Exchange exchange = call(HttpMethod.GET, "flamegraph", EMPTY_BODY);

        assertEquals(UNKNOWN_ENDPOINT_MESSAGE, exchange.text());
        assertNull(exchange.response());
    }

    /**
     * A body this endpoint cannot read is the client's mistake. Reported as a 500, Microscope reads
     * it as the window having failed and repeats the whole scan over.
     */
    public void testABodyThatCannotBeReadIsABadRequest() throws IOException {
        assertEquals(HttpResponseStatus.BAD_REQUEST, call(HttpMethod.POST, NAVIGATE, BROKEN_BODY).status());
        assertEquals(HttpResponseStatus.BAD_REQUEST, call(HttpMethod.POST, RESOLVE, BROKEN_BODY).status());
    }

    /** An empty body parses to JSON null, and asking that for an object is an {@code IllegalStateException}. */
    public void testAnEmptyOrNonObjectBodyIsABadRequest() throws IOException {
        assertEquals(HttpResponseStatus.BAD_REQUEST, call(HttpMethod.POST, NAVIGATE, EMPTY_BODY).status());
        assertEquals(HttpResponseStatus.BAD_REQUEST, call(HttpMethod.POST, NAVIGATE, NOT_AN_OBJECT_BODY).status());
    }

    /** Switched off, the IDE is invisible to Microscope's scan rather than visible and refusing. */
    public void testADisabledIdeAnswersNothingAtAll() throws IOException {
        JeffreySettings.getInstance().setEnabled(false);

        assertEquals(HttpResponseStatus.NOT_FOUND, call(HttpMethod.GET, PING, EMPTY_BODY).status());
        assertEquals(HttpResponseStatus.NOT_FOUND, call(HttpMethod.POST, NAVIGATE, EMPTY_BODY).status());
    }

    public void testOnlyReadAndWriteVerbsReachTheTable() {
        assertTrue(service.isMethodSupported(HttpMethod.GET));
        assertTrue(service.isMethodSupported(HttpMethod.POST));
        assertFalse(service.isMethodSupported(HttpMethod.PUT));
        assertFalse(service.isMethodSupported(HttpMethod.DELETE));
    }

    /** Microscope's backend calls server-side and sends no {@code Origin}; those are always allowed. */
    public void testARequestWithoutAnOriginIsAllowed() {
        FullHttpRequest request = request(HttpMethod.GET, PING, EMPTY_BODY);

        assertEquals(HttpRequestHandler.OriginCheckResult.ALLOW, service.isOriginAllowed(request));
    }

    public void testARequestWithAnOriginIsLeftToThePlatform() {
        FullHttpRequest request = request(HttpMethod.GET, PING, EMPTY_BODY);
        request.headers().set(HttpHeaderNames.ORIGIN, "http://evil.example");

        assertFalse(HttpRequestHandler.OriginCheckResult.ALLOW.equals(service.isOriginAllowed(request)));
    }

    private Exchange call(HttpMethod method, String path, String body) throws IOException {
        FullHttpRequest request = request(method, path, body);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        try {
            ChannelHandlerContext context = channel.pipeline().firstContext();
            String text = service.execute(new QueryStringDecoder(request.uri()), request, context);
            channel.flushOutbound();
            Object outbound = channel.readOutbound();
            return new Exchange(text, outbound instanceof HttpResponse response ? response : null);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static FullHttpRequest request(HttpMethod method, String path, String body) {
        return new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                method,
                ENDPOINT_PREFIX + path,
                Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
    }

    /** What one call produced: the error string {@code execute} returns, and the response it wrote. */
    private record Exchange(String text, HttpResponse response) {

        private HttpResponseStatus status() {
            if (response == null) {
                throw new AssertionError("no response was written");
            }
            return response.status();
        }

        private String body() {
            if (response instanceof FullHttpResponse full) {
                return full.content().toString(StandardCharsets.UTF_8);
            }
            return "";
        }
    }
}
