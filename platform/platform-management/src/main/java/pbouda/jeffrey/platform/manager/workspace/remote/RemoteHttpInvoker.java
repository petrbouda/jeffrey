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

package pbouda.jeffrey.platform.manager.workspace.remote;

import cafe.jeffrey.jfr.events.http.HttpClientExchangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import pbouda.jeffrey.shared.common.exception.ErrorResponse;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.exception.JeffreyException;

import java.net.URI;
import java.util.function.Supplier;

/**
 * Encapsulates all cross-cutting HTTP concerns for remote Jeffrey communication:
 * JFR event tracking, error handling, logging, and RestClient management.
 */
public class RemoteHttpInvoker {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteHttpInvoker.class);

    private final RestClient restClient;
    private final URI uri;

    public RemoteHttpInvoker(URI uri, RestClient restClient) {
        this.uri = uri;
        this.restClient = restClient;
    }

    public RestClient restClient() {
        return restClient;
    }

    public URI uri() {
        return uri;
    }

    public <T> ResponseEntity<T> get(Supplier<ResponseEntity<T>> invocation) {
        return invoke(HttpMethod.GET, invocation);
    }

    public <T> ResponseEntity<T> post(Supplier<ResponseEntity<T>> invocation) {
        return invoke(HttpMethod.POST, invocation);
    }

    public void delete(Supplier<ResponseEntity<Void>> invocation) {
        invoke(HttpMethod.DELETE, invocation);
    }

    /**
     * Executes a streaming HTTP invocation and tracks the JFR event with the actual HTTP status.
     *
     * @param invocation the streaming invocation that returns the HTTP status code on success
     */
    public void streaming(StreamingInvocation invocation) {
        HttpClientExchangeEvent event = new HttpClientExchangeEvent();

        int statusCode = -1;
        try {
            statusCode = invocation.execute();
        } catch (ResourceAccessException e) {
            throw Exceptions.remoteJeffreyUnavailable(uri, e);
        } finally {
            event.end();
            if (event.shouldCommit()) {
                event.remoteHost = uri.getHost();
                event.remotePort = uri.getPort();
                event.method = HttpMethod.POST.name();
                event.mediaType = MediaType.APPLICATION_JSON_VALUE;
                event.status = statusCode;
                event.commit();
            }
        }
    }

    /**
     * Functional interface for streaming invocations that return the HTTP status code.
     */
    @FunctionalInterface
    public interface StreamingInvocation {
        int execute();
    }

    private <T> ResponseEntity<T> invoke(HttpMethod method, Supplier<ResponseEntity<T>> invocation) {
        HttpClientExchangeEvent event = new HttpClientExchangeEvent();

        int statusCode = -1;
        try {
            return invocation.get();
        } catch (ResourceAccessException e) {
            throw Exceptions.remoteJeffreyUnavailable(uri, e);
        } catch (HttpStatusCodeException e) {
            statusCode = e.getStatusCode().value();
            throw toRemoteError(statusCode, () -> e.getResponseBodyAs(ErrorResponse.class));
        } finally {
            event.end();
            if (event.shouldCommit()) {
                event.remoteHost = uri.getHost();
                event.remotePort = uri.getPort();
                event.method = method.name();
                event.mediaType = MediaType.APPLICATION_JSON_VALUE;
                event.status = statusCode;
                event.commit();
            }
        }
    }

    /**
     * Attempts to parse an {@link ErrorResponse} from a remote error. If the response body is not JSON
     * (e.g., an HTML error page from a reverse proxy), falls back to a generic error message.
     * Uses {@link Exceptions#fromRemoteErrorResponse} to preserve remote origin context.
     */
    public JeffreyException toRemoteError(int statusCode, Supplier<ErrorResponse> errorResponseSupplier) {
        try {
            ErrorResponse error = errorResponseSupplier.get();
            LOG.warn("Remote Jeffrey returned an error: uri={} error={}", uri, error);
            return Exceptions.fromRemoteErrorResponse(uri, error);
        } catch (Exception ex) {
            LOG.warn("Remote Jeffrey returned a non-JSON error: uri={} status={}", uri, statusCode);
            return Exceptions.internal("Remote Jeffrey error (HTTP " + statusCode + ")");
        }
    }
}
