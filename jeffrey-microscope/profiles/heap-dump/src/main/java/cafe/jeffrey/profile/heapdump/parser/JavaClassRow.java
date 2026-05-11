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
package cafe.jeffrey.profile.heapdump.parser;

/**
 * One row of the {@code class} table.
 *
 * Nullable fields ({@code superClassId}, {@code classloaderId}, {@code signersId},
 * {@code protectionDomainId}) are {@code null} when the corresponding HPROF id
 * is 0 ("no reference") — the index translates 0 to NULL on write so callers
 * don't have to special-case the convention.
 */
public record JavaClassRow(
        long classId,
        int classSerial,
        String name,
        boolean isArray,
        Long superClassId,
        Long classloaderId,
        Long signersId,
        Long protectionDomainId,
        int instanceSize,
        int staticFieldsSize,
        long fileOffset) {

    public JavaClassRow {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
    }
}
