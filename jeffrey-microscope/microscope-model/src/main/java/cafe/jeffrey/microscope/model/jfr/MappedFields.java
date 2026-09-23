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

package cafe.jeffrey.microscope.model.jfr;

/**
 * One event's fields, ready to store: the JSON text and, separately, the one value that was lifted
 * out of it to be pooled.
 *
 * @param json        the event's fields as JSON, without {@link #pooledField} if one was lifted
 * @param pooledField the key whose value was lifted out, or {@code null} when nothing qualified
 * @param pooledText  the lifted text, or {@code null} when nothing qualified
 */
public record MappedFields(String json, String pooledField, String pooledText) {

    public boolean hasPooledField() {
        return pooledField != null;
    }
}
