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
package cafe.jeffrey.profile.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the toolset's default {@link McpToolAnnotations} for one tool.
 * <p>
 * Read-only-ness is a family-level property almost everywhere in Jeffrey, so a toolset declares it once
 * and this annotation exists for the handful of methods that differ from their neighbours — the one tool
 * in an otherwise read-only family that writes, or the one that reaches a hub.
 * <p>
 * Spring AI's own {@code @Tool} carries no such attributes, which is why this is Jeffrey's own.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpToolHints {

    boolean readOnly() default true;

    boolean destructive() default false;

    boolean idempotent() default true;

    boolean openWorld() default false;
}
