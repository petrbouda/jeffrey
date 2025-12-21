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

package pbouda.jeffrey.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/**
 * Utility class for MessagePack serialization.
 * Uses Jackson with MessagePack format for compact binary serialization.
 * Typically 30-50% smaller than JSON for structured data.
 */
public abstract class MsgPack {

    public static final String MEDIA_TYPE = "application/msgpack";

    private static final ObjectMapper MAPPER = new ObjectMapper(new MessagePackFactory())
            .registerModule(new JavaTimeModule());

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Serializes an object to MessagePack binary format.
     *
     * @param obj the object to serialize
     * @return MessagePack encoded bytes
     */
    public static byte[] toBytes(Object obj) {
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object to MessagePack", e);
        }
    }

    /**
     * Deserializes MessagePack bytes to an object.
     *
     * @param bytes the MessagePack encoded bytes
     * @param clazz the class to deserialize to
     * @param <T>   the type of the object
     * @return the deserialized object
     */
    public static <T> T fromBytes(byte[] bytes, Class<T> clazz) {
        try {
            return MAPPER.readValue(bytes, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize MessagePack bytes", e);
        }
    }
}
