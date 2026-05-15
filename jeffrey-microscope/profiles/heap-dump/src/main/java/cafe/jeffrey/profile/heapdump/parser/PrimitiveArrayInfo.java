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
 * In-memory metadata the indexer needs to decode every {@code java.lang.String}'s
 * backing primitive array: the triplet that the string-content writer would
 * otherwise read with a SQL PK lookup. Populated by Pass B (one entry per
 * {@code PRIMITIVE_ARRAY_DUMP}), consumed by the string-content phase. Held
 * only during the index build.
 */
public record PrimitiveArrayInfo(long fileOffset, int arrayLength, int elementType) {
}
