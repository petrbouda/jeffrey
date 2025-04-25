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

package pbouda.jeffrey.resources;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pbouda.jeffrey.manager.ProjectsManager;

@Component
@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {

    @Autowired
    public JerseyConfig(ProjectsManager projectsManager) {
        // To make it injectable for ProjectsResource
        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(projectsManager).to(ProjectsManager.class);
            }
        });

        register(RootResource.class);
        register(JacksonFeature.class);
        register(MultiPartFeature.class);
        register(CORSFilter.class);

        register(InvalidUserInputExceptionMapper.class);
    }

    public static class CORSFilter implements ContainerResponseFilter {
        @Override
        public void filter(ContainerRequestContext request, ContainerResponseContext response) {
            response.getHeaders().add("Access-Control-Allow-Origin", "*");
            response.getHeaders().add("Access-Control-Allow-Headers", "*");
            response.getHeaders().add("Access-Control-Allow-Credentials", "true");
            response.getHeaders().add("Access-Control-Allow-Methods",
                    "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        }
    }
}
