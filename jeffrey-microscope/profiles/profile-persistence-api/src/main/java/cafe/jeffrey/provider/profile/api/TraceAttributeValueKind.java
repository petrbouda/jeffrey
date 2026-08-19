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

package cafe.jeffrey.provider.profile.api;

/**
 * What a key's values turned out to be, inferred from the values themselves rather than declared.
 * <p>
 * It decides two things: which operators a caller is offered, and whether a key's values are
 * bucketed by order of magnitude when they are ranked. A key whose values are all {@code true} or
 * {@code false} is called out separately from a string because a two-valued key is a switch, and
 * offering it a substring match reads as a mistake.
 */
public enum TraceAttributeValueKind {

    STRING,
    NUMBER,
    BOOLEAN
}
