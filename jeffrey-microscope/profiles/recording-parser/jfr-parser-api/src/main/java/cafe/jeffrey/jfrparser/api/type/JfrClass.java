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
package cafe.jeffrey.jfrparser.api.type;

public interface JfrClass {
    /**
     * The class name with any hidden-class address stripped off, so it is stable across runs.
     */
    String className();

    /**
     * The per-run identity a hidden class carries in its name (e.g. {@code 0x0000000011cb1be8}),
     * or {@code null} when the class is not hidden. Only the ingest paths that parse a recording
     * know this, so it defaults to "not hidden" and stays a single-abstract-method interface.
     */
    default String hiddenClassId() {
        return null;
    }

    default boolean isHidden() {
        return hiddenClassId() != null;
    }
}
