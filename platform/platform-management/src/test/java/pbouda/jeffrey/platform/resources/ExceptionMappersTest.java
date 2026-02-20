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

package pbouda.jeffrey.platform.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.shared.common.exception.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that all exception types are properly mapped to JSON ErrorResponse bodies
 * with correct HTTP status codes and Content-Type headers.
 */
class ExceptionMappersTest extends AbstractResourceTest {

    @Path("/test-errors")
    @Produces(MediaType.APPLICATION_JSON)
    public static class ErrorTestResource {

        @GET
        @Path("/jeffrey-client")
        public String throwJeffreyClient() {
            throw Exceptions.invalidRequest("Invalid field value");
        }

        @GET
        @Path("/jeffrey-not-found")
        public String throwJeffreyNotFound() {
            throw Exceptions.projectNotFound("proj-123");
        }

        @GET
        @Path("/jeffrey-internal")
        public String throwJeffreyInternal() {
            throw Exceptions.internal("Something broke");
        }

        @GET
        @Path("/jersey-not-found")
        public String throwJerseyNotFound() {
            throw new NotFoundException("Resource does not exist");
        }

        @GET
        @Path("/illegal-argument")
        public String throwIllegalArgument() {
            throw new IllegalArgumentException("Bad argument");
        }

        @GET
        @Path("/generic")
        public String throwGeneric() {
            throw new RuntimeException("Unexpected error");
        }
    }

    @Override
    protected void configureResources(ResourceConfig config) {
        config.register(ErrorTestResource.class);
    }

    @Nested
    class JeffreyClientException {

        @Test
        void returns400_withErrorResponse() {
            Response response = target("/test-errors/jeffrey-client")
                    .request(MediaType.APPLICATION_JSON)
                    .get();

            assertEquals(400, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

            ErrorResponse error = response.readEntity(ErrorResponse.class);
            assertEquals(ErrorType.CLIENT, error.type());
            assertEquals(ErrorCode.INVALID_REQUEST, error.code());
            assertEquals("Invalid field value", error.message());
        }
    }

    @Nested
    class JeffreyNotFoundExceptions {

        @Test
        void returns404_withErrorResponse() {
            Response response = target("/test-errors/jeffrey-not-found")
                    .request(MediaType.APPLICATION_JSON)
                    .get();

            assertEquals(404, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

            ErrorResponse error = response.readEntity(ErrorResponse.class);
            assertEquals(ErrorType.CLIENT, error.type());
            assertEquals(ErrorCode.PROJECT_NOT_FOUND, error.code());
            assertTrue(error.message().contains("proj-123"));
        }
    }

    @Nested
    class JeffreyInternalExceptions {

        @Test
        void returns500_withErrorResponse() {
            Response response = target("/test-errors/jeffrey-internal")
                    .request(MediaType.APPLICATION_JSON)
                    .get();

            assertEquals(500, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

            ErrorResponse error = response.readEntity(ErrorResponse.class);
            assertEquals(ErrorType.INTERNAL, error.type());
            assertEquals(ErrorCode.UNKNOWN_ERROR_RESPONSE, error.code());
            assertEquals("Something broke", error.message());
        }
    }

    @Nested
    class JerseyNotFoundExceptions {

        @Test
        void returns404_withJsonErrorResponse() {
            Response response = target("/test-errors/jersey-not-found")
                    .request(MediaType.APPLICATION_JSON)
                    .get();

            assertEquals(404, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

            ErrorResponse error = response.readEntity(ErrorResponse.class);
            assertEquals(ErrorType.CLIENT, error.type());
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.code());
        }

        @Test
        void returns404_forNonExistentPath() {
            Response response = target("/test-errors/does-not-exist")
                    .request(MediaType.APPLICATION_JSON)
                    .get();

            assertEquals(404, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

            ErrorResponse error = response.readEntity(ErrorResponse.class);
            assertEquals(ErrorType.CLIENT, error.type());
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.code());
        }
    }

    @Nested
    class IllegalArgumentExceptions {

        @Test
        void returns400_withErrorResponse() {
            Response response = target("/test-errors/illegal-argument")
                    .request(MediaType.APPLICATION_JSON)
                    .get();

            assertEquals(400, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

            ErrorResponse error = response.readEntity(ErrorResponse.class);
            assertEquals(ErrorType.CLIENT, error.type());
            assertEquals(ErrorCode.INVALID_REQUEST, error.code());
            assertEquals("Bad argument", error.message());
        }
    }

    @Nested
    class GenericExceptions {

        @Test
        void returns500_withErrorResponse() {
            Response response = target("/test-errors/generic")
                    .request(MediaType.APPLICATION_JSON)
                    .get();

            assertEquals(500, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

            ErrorResponse error = response.readEntity(ErrorResponse.class);
            assertEquals(ErrorType.INTERNAL, error.type());
            assertEquals(ErrorCode.UNKNOWN_ERROR_RESPONSE, error.code());
            assertEquals("Unexpected error", error.message());
        }
    }
}
