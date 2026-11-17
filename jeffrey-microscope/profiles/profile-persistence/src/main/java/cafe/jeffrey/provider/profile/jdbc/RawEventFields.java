/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

/**
 * The {@code events} view's own splice, for a statement that reads {@code events_raw} directly.
 */
final class RawEventFields {

    /**
     * The view's expression, applied to a raw row aliased {@code e} joined to {@code field_texts t}.
     * <p>
     * A statement reads {@code events_raw} rather than the view when it needs {@code rowid}, which only
     * a physical table has — but its payload still has to be the JSON the recording declared, pooled
     * field included. Rehydrating with the view's own expression is what keeps what such a statement
     * reads identical to what a reader of the view sees.
     */
    static final String REHYDRATED = """
            CASE WHEN e.pooled_text_hash IS NULL
                 THEN e.fields
                 ELSE json_merge_patch(e.fields, json_object(e.pooled_field, t.text)) END""";

    private RawEventFields() {
    }
}
