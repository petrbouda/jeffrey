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

package pbouda.jeffrey.resources;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import pbouda.jeffrey.jfr.types.http.HttpExchangeEvent;

import java.io.IOException;

@Provider
public class JfrHttpEventFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private final HttpServletRequest servletRequest;

    public JfrHttpEventFilter(@Context HttpServletRequest servletRequest) {
        this.servletRequest = servletRequest;
    }

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        HttpExchangeEvent event = new HttpExchangeEvent();
        if (!event.isEnabled()) {
            return;
        }
        event.begin();
        request.setProperty(HttpExchangeEvent.NAME, event);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
        HttpExchangeEvent event = (HttpExchangeEvent) request.getProperty(HttpExchangeEvent.NAME);
        if (event == null || !event.isEnabled()) {
            return;
        }
        event.end();
        if (event.shouldCommit()) {
            event.remoteHost = servletRequest.getRemoteHost();
            event.remotePort = servletRequest.getRemotePort();
            event.uri = request.getUriInfo().getRequestUri().getRawPath();
            event.method = request.getMethod();
            event.mediaType = request.getMediaType() != null ? request.getMediaType().toString() : null;
            event.queryParams = request.getUriInfo()
                    .getQueryParameters().toString();
            event.requestLength = request.getLength();
            event.responseLength = response.getLength();
            event.status = response.getStatus();
            event.commit();
        }
    }
}
