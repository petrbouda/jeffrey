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

package cafe.jeffrey.jfr.events.spring;

import cafe.jeffrey.jfr.events.http.HttpClientExchangeEvent;
import cafe.jeffrey.jfr.events.trace.TracedEvents;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.function.Function;

/**
 * Records an outbound HTTP call as a <b>leaf span</b> nested under whatever span is in progress —
 * usually the server exchange of the request being served.
 * <p>
 * A leaf, not a scope: the downstream work happens in another process this recording cannot see, so
 * there is nothing to nest underneath it. {@link TracedEvents#emit} applies the whole lifecycle —
 * the guard when nothing is recording, the interval, the failure recording, and the stamping commit
 * — so this class only says which fields the call fills.
 * <p>
 * A transport failure that never produced a status code is recorded through
 * {@code failed(Throwable)} by {@code emit}, so a connection refused shows red in the trace rather
 * than silently green with status {@code 0}.
 *
 * <h2>Cardinality</h2>
 * {@code uri} must not carry entity ids, or the HTTP client dashboard grows one "endpoint" per
 * call. By default only scheme, host, port and path are recorded, with the query string dropped;
 * pass a normaliser to collapse variable path segments into a template.
 */
public class JfrClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final int NO_STATUS = 0;

    private final Function<URI, String> uriNaming;

    /**
     * @param uriNaming turns the request's URI into a stable, low-cardinality name
     */
    public JfrClientHttpRequestInterceptor(Function<URI, String> uriNaming) {
        this.uriNaming = Objects.requireNonNull(uriNaming, "uriNaming must not be null");
    }

    /**
     * The form that records host and path with the query string dropped.
     */
    public JfrClientHttpRequestInterceptor() {
        this(JfrClientHttpRequestInterceptor::hostAndPath);
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        HttpClientExchangeEvent event = new HttpClientExchangeEvent();
        return TracedEvents.emit(event,
                () -> execution.execute(request, body),
                (emitted, response) -> {
                    emitted.method = request.getMethod().name();
                    emitted.uri = uriNaming.apply(request.getURI());
                    emitted.remoteHost = request.getURI().getHost();
                    emitted.remotePort = request.getURI().getPort();
                    emitted.requestLength = body == null ? 0 : body.length;
                    // null when the call threw before answering; failed() already recorded why.
                    emitted.statusCode = response == null ? NO_STATUS : statusOf(response);
                });
    }

    private static int statusOf(ClientHttpResponse response) {
        try {
            return response.getStatusCode().value();
        } catch (IOException e) {
            return NO_STATUS;
        }
    }

    /**
     * Host and path, without the query string — which is where ids and tokens usually live.
     */
    private static String hostAndPath(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost();
        String path = uri.getPath() == null ? "" : uri.getPath();
        return host + path;
    }
}
