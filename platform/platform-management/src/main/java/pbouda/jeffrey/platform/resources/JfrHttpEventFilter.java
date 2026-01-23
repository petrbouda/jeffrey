/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.resources;

import cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.server.ExtendedUriInfo;
import pbouda.jeffrey.shared.common.Json;

import java.io.IOException;

@Provider
public class JfrHttpEventFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private final HttpServletRequest servletRequest;
    private final ExtendedUriInfo extendedUriInfo;

    public JfrHttpEventFilter(
            @Context HttpServletRequest servletRequest,
            @Context ExtendedUriInfo extendedUriInfo) {

        this.servletRequest = servletRequest;
        this.extendedUriInfo = extendedUriInfo;
    }

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        HttpServerExchangeEvent event = new HttpServerExchangeEvent();
        if (!event.isEnabled()) {
            return;
        }
        event.begin();
        request.setProperty(HttpServerExchangeEvent.NAME, event);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
        HttpServerExchangeEvent event = (HttpServerExchangeEvent) request.getProperty(HttpServerExchangeEvent.NAME);
        if (event == null || !event.isEnabled()) {
            return;
        }
        event.end();
        if (event.shouldCommit()) {
            event.remoteHost = servletRequest.getRemoteHost();
            event.remotePort = servletRequest.getRemotePort();
            event.uri = getUriWithTemplates(extendedUriInfo, request);
            event.method = request.getMethod();
            event.mediaType = request.getMediaType() != null ? request.getMediaType().toString() : null;
            event.queryParams = Json.toString(request.getUriInfo().getQueryParameters());
            event.pathParams = Json.toString(request.getUriInfo().getPathParameters());
            event.requestLength = getContentLength(request);
            event.responseLength = getContentLength(response);
            event.status = response.getStatus();
            event.commit();
        }
    }

    /**
     * Gets the content length from a request context as a long.
     * Reads the Content-Length header directly to support values > 2GB.
     * Returns -1 if the header is missing or cannot be parsed.
     */
    private long getContentLength(ContainerRequestContext request) {
        String contentLength = request.getHeaderString(HttpHeaders.CONTENT_LENGTH);
        if (contentLength == null || contentLength.isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(contentLength);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Gets the content length from a response context as a long.
     * Reads the Content-Length header directly to support values > 2GB.
     * Returns -1 if the header is missing or cannot be parsed.
     */
    private long getContentLength(ContainerResponseContext response) {
        String contentLength = response.getHeaderString(HttpHeaders.CONTENT_LENGTH);
        if (contentLength == null || contentLength.isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(contentLength);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String getUriWithTemplates(ExtendedUriInfo extendedUriInfo, ContainerRequestContext request) {
        // Get the matched resource templates paths from ExtendedUriInfo
        if (extendedUriInfo != null && !extendedUriInfo.getMatchedTemplates().isEmpty()) {
            StringBuilder patternUri = new StringBuilder();

            // Get base path if not deployed at root
            String basePath = extendedUriInfo.getBaseUri().getPath();
            if (!basePath.equals("/")) {
                patternUri.append(basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath);
            }

            // Process templates in reverse order (from least specific to most specific)
            for (int i = extendedUriInfo.getMatchedTemplates().size() - 1; i >= 0; i--) {
                String template = extendedUriInfo.getMatchedTemplates().get(i).getTemplate();

                // Clean the template
                if (!template.startsWith("/")) {
                    template = "/" + template;
                }

                // Avoid double slashes when joining
                if (!patternUri.isEmpty() && patternUri.charAt(patternUri.length() - 1) == '/' && template.startsWith("/")) {
                    template = template.substring(1);
                }

                patternUri.append(template);
            }

            // Make sure we don't have trailing slash unless it's just "/"
            String result = patternUri.toString();
            if (result.length() > 1 && result.endsWith("/")) {
                result = result.substring(0, result.length() - 1);
            }

            return result;
        }

        // Fallback to the raw path if no templates available
        return extendedUriInfo.getRequestUri().getPath();
    }
}
