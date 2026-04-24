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

package pbouda.jeffrey.shared.jackson.jaxrs;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import pbouda.jeffrey.shared.common.Json;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * JAX-RS reader/writer that serialises and deserialises {@code application/json}
 * (and {@code application/*+json}) payloads via Jackson 3 ({@code tools.jackson}),
 * using the shared {@link Json#mapper()} so custom serializers registered there
 * (e.g. for {@code Type}, {@code RelativeTimeRange}) apply uniformly at the HTTP
 * boundary.
 *
 * <p>This replaces Jersey's default {@code org.glassfish.jersey.jackson.JacksonFeature},
 * which targets Jackson 2 ({@code com.fasterxml.jackson}) and therefore serialises
 * Jackson-3 {@code JsonNode}/{@code ObjectNode}/{@code ArrayNode} as reflective
 * POJOs (exposing internal accessor properties like {@code nodeType}, {@code empty},
 * {@code object} rather than the tree itself).
 */
@Provider
@Consumes({MediaType.APPLICATION_JSON, "application/*+json"})
@Produces({MediaType.APPLICATION_JSON, "application/*+json"})
public class JacksonJsonProvider implements MessageBodyReader<Object>, MessageBodyWriter<Object> {

    private final ObjectMapper mapper;

    public JacksonJsonProvider() {
        this(Json.mapper());
    }

    JacksonJsonProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean isReadable(
            Class<?> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType) {
        return isJson(mediaType);
    }

    @Override
    public Object readFrom(
            Class<Object> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType,
            MultivaluedMap<String, String> httpHeaders,
            InputStream entityStream) throws IOException {
        JavaType javaType = mapper.getTypeFactory()
                .constructType(genericType != null ? genericType : type);
        return mapper.readValue(entityStream, javaType);
    }

    @Override
    public boolean isWriteable(
            Class<?> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType) {
        return isJson(mediaType);
    }

    @Override
    public long getSize(
            Object value,
            Class<?> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(
            Object value,
            Class<?> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException {
        mapper.writeValue(entityStream, value);
    }

    private static boolean isJson(MediaType mediaType) {
        if (mediaType == null) {
            return true;
        }
        String subtype = mediaType.getSubtype();
        return "application".equalsIgnoreCase(mediaType.getType())
                && (subtype.equalsIgnoreCase("json") || subtype.toLowerCase().endsWith("+json"));
    }
}
