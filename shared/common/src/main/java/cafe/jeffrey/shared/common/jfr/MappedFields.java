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

package cafe.jeffrey.shared.common.jfr;

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
