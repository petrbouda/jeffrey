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

package pbouda.jeffrey.server.core.web;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import pbouda.jeffrey.shared.common.Json;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * HTTP message converter that serialises/deserialises {@code application/json}
 * payloads via the shared Jackson 3 {@link Json#mapper()}. Replaces the JAX-RS
 * {@code JacksonJsonProvider}.
 */
public class JacksonJson3HttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {

    private final ObjectMapper mapper;

    public JacksonJson3HttpMessageConverter() {
        this(Json.mapper());
    }

    public JacksonJson3HttpMessageConverter(ObjectMapper mapper) {
        super(MediaType.APPLICATION_JSON, MediaType.valueOf("application/*+json"));
        setDefaultCharset(StandardCharsets.UTF_8);
        this.mapper = mapper;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return true;
    }

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        JavaType javaType = mapper.getTypeFactory().constructType(type);
        return mapper.readValue(inputMessage.getBody(), javaType);
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        return mapper.readValue(inputMessage.getBody(), clazz);
    }

    @Override
    protected void writeInternal(Object t, Type type, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        mapper.writeValue(outputMessage.getBody(), t);
    }
}
