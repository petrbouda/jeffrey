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

package cafe.jeffrey.jfrparser.api.type;

/**
 * Plain in-memory {@link JfrMethod}. Shared by every parser path (DB row mappers, in-memory JFR
 * parsing) so a class/method pair has a single canonical carrier.
 */
public record JfrMethodImpl(String className, String methodName) implements JfrMethod, JfrClass {

    /**
     * Parses an entity of the form {@code Class#method} (or just {@code Class}) into a method.
     */
    public static JfrMethod of(String entity) {
        if (entity == null || entity.isBlank()) {
            return null;
        }

        String[] split = entity.split("#");
        if (split.length == 0) {
            return null;
        }

        if (split.length == 2) {
            return new JfrMethodImpl(split[0], split[1]);
        } else {
            return new JfrMethodImpl(split[0], null);
        }
    }

    public static JfrClass ofClass(String className) {
        return new JfrMethodImpl(className, null);
    }

    @Override
    public JfrClass clazz() {
        return this;
    }
}
