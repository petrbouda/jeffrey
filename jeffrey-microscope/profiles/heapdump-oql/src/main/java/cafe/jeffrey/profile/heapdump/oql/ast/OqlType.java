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
package cafe.jeffrey.profile.heapdump.oql.ast;

/**
 * Coarse type tags assigned by the type resolver. We're not building a real
 * type system — these are just enough to validate operand compatibility and
 * pick the right code path in the compiler (e.g. an {@link #INSTANCE_REF} on
 * the right side of an attribute access vs. a {@link #STRING} on the right of
 * a {@code LIKE}).
 */
public enum OqlType {
    /** Reference to a heap instance (an instance_id value). */
    INSTANCE_REF,
    /** Reference to a heap class (a class_id value). */
    CLASS_REF,
    /** Integer or floating-point scalar. */
    NUMBER,
    /** Decoded character data — for string predicates and accessors. */
    STRING,
    /** Boolean result of a predicate. */
    BOOLEAN,
    /** Result of a subquery that yields a column of instance refs. */
    SET_OF_INSTANCES,
    /** Used during early resolution before a concrete type is known. */
    UNKNOWN
}
