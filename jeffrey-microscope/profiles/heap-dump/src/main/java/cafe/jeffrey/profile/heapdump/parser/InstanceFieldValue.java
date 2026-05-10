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
 * A single decoded instance field value.
 *
 * {@code value} is boxed:
 * <ul>
 *   <li>OBJECT → {@link Long} (referenced instance id; 0 if null reference)</li>
 *   <li>BOOLEAN → {@link Boolean}</li>
 *   <li>BYTE → {@link Byte}</li>
 *   <li>CHAR → {@link Character}</li>
 *   <li>SHORT → {@link Short}</li>
 *   <li>INT → {@link Integer}</li>
 *   <li>FLOAT → {@link Float}</li>
 *   <li>LONG → {@link Long}</li>
 *   <li>DOUBLE → {@link Double}</li>
 * </ul>
 */
public record InstanceFieldValue(String name, int basicType, Object value) {
}
