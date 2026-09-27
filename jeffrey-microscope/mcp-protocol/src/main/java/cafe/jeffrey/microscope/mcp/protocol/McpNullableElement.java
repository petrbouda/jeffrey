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


package cafe.jeffrey.microscope.mcp.protocol;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the element type of a list or set, or the value type of a map, in an output record as one that
 * may be {@code null}: {@code List<@McpNullableElement String>} is an array whose items are
 * {@code ["string", "null"]}. The container itself stays non-null unless its component is also
 * {@link McpNullable} — a SQL row is always there, while a cell in it can be NULL.
 * <p>
 * A type annotation of its own, rather than {@link McpNullable} widened to type uses: a type-use
 * annotation cannot precede a qualified type such as {@code Outer.Inner}, which many components are.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
public @interface McpNullableElement {
}
