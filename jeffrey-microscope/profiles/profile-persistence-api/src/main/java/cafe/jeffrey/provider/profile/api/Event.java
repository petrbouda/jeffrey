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

package cafe.jeffrey.provider.profile.api;

import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

public record Event(
        String eventType,
        Instant startTimestamp,
        Long duration,
        long samples,
        Long weight,
        String weightEntity,
        Long stacktraceId,
        Long threadId,
        ObjectNode fields,
        // The one field the reader pooled out of `fields`, or null when nothing qualified.
        // See SingleThreadedEventWriter#onFieldText.
        PooledField pooledField) {

    /**
     * Which field was lifted out of {@link #fields()} and where its text lives — the field's key
     * and the pooled text's id. The reader decides what to pool by size alone, so the key can be
     * anything an event type declares.
     */
    public record PooledField(String field, long textHash) {
    }
}
