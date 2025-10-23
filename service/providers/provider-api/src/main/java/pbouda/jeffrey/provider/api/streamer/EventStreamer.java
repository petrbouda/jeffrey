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

package pbouda.jeffrey.provider.api.streamer;


import pbouda.jeffrey.provider.api.builder.RecordBuilder;

public interface EventStreamer<T> {

    /**
     * Starts streaming the events from the database, mapping them to the provided type and building
     * a result using the RecordBuilder.
     *
     * @param builder The RecordBuilder to use for processing the records.
     * @param <R>     The type of the result produced by the RecordBuilder.
     * @return The result produced by the RecordBuilder after processing all records.
     */
    <R> R startStreaming(RecordBuilder<T, R> builder);
}
