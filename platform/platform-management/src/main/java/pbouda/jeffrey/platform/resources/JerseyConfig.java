/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.logging.LoggingFeature.Verbosity;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.sse.SseFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {

    @Autowired
    public JerseyConfig(
            @Value("${jeffrey.logging.http-access.enabled:false}") boolean isAccessLoggingEnabled,
            @Value("${jeffrey.cors.mode:PROD}") CorsMode corsMode) {

        // Scan for resources in core and profile-management modules
        packages("pbouda.jeffrey.resources", "pbouda.jeffrey.profile.resources");

        register(RootInternalResource.class);
        register(RootPublicResource.class);
        register(JacksonFeature.class);
        register(MultiPartFeature.class);
        register(SseFeature.class);
        if (corsMode == CorsMode.PROD) {
            register(ProductionCORSFilter.class);
        } else {
            register(DevCORSFilter.class);
        }

        if (isAccessLoggingEnabled) {
            LoggingFeature loggingFeature = new LoggingFeature(
                    Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
                    Level.INFO,
                    Verbosity.PAYLOAD_ANY,
                    10000);

            register(loggingFeature);
        }

        // Register JFR HTTP event filter
        register(JfrHttpEventFilter.class);

        register(ExceptionMappers.JeffreyExceptionMapper.class);
        register(ExceptionMappers.IllegalArgumentException.class);
        register(ExceptionMappers.GenericException.class);
    }

    public static class ProductionCORSFilter implements ContainerResponseFilter {
        @Override
        public void filter(ContainerRequestContext request, ContainerResponseContext response) {
            String path = request.getUriInfo().getPath();
            // Only allow CORS for public API endpoints (for Jeffrey-to-Jeffrey communication)
            // Internal API has no CORS headers - browser enforces same-origin policy
            if (path.startsWith("public/")) {
                response.getHeaders().add("Access-Control-Allow-Origin", "*");
                response.getHeaders().add("Access-Control-Allow-Headers", "*");
                response.getHeaders().add("Access-Control-Allow-Credentials", "true");
                response.getHeaders().add("Access-Control-Allow-Methods",
                        "GET, POST, PUT, DELETE, OPTIONS, HEAD");
                response.getHeaders().add("Access-Control-Expose-Headers", "Content-Disposition");
            }
        }
    }

    public static class DevCORSFilter implements ContainerResponseFilter {
        @Override
        public void filter(ContainerRequestContext request, ContainerResponseContext response) {
            response.getHeaders().add("Access-Control-Allow-Origin", "*");
            response.getHeaders().add("Access-Control-Allow-Headers", "*");
            response.getHeaders().add("Access-Control-Allow-Credentials", "true");
            response.getHeaders().add("Access-Control-Allow-Methods",
                    "GET, POST, PUT, DELETE, OPTIONS, HEAD");
            response.getHeaders().add("Access-Control-Expose-Headers", "Content-Disposition");
        }
    }
}
