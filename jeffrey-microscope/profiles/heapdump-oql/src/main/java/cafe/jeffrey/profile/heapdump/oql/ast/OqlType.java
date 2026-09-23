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
