/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.api;

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
        // The event's fields as JSON text. Text rather than a tree because that is all the
        // writer ever needed: a tree was built per event only to be serialized again here.
        String fields,
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
