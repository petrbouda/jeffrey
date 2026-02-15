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

package pbouda.jeffrey.platform.queue;

/**
 * Strategy interface for serializing and deserializing event payloads
 * to and from their string (JSON) representation for queue storage.
 *
 * @param <T> the type of the event payload
 */
public interface EventSerializer<T> {

    /**
     * Serializes the event payload to a string representation (typically JSON).
     *
     * @param event the event to serialize
     * @return the serialized string representation
     */
    String serialize(T event);

    /**
     * Deserializes a string representation back into an event payload.
     *
     * @param payload the serialized string representation
     * @return the deserialized event
     */
    T deserialize(String payload);
}
