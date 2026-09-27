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
package cafe.jeffrey.profile.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The default and the range of a numeric parameter, emitted as JSON-Schema {@code default},
 * {@code minimum} and {@code maximum} — or, for a bound the tool refuses at the bound itself,
 * {@code exclusiveMinimum}/{@code exclusiveMaximum}, which JSON Schema 2020-12 spells as the bound's
 * number rather than as a flag beside {@code minimum}.
 * <p>
 * They lived in the prose of the description before — "default 20, max 50" — where a client cannot
 * act on them. Every element is optional; one left at {@link #UNSET} is not emitted.
 * <p>
 * The range is advice to the client, not a gate in front of the tool: the tools clamp a limit rather
 * than refuse it (an omitted or non-positive one takes the default, one above the maximum takes the
 * maximum), so a value outside the range still reaches the tool and is answered. What is checked,
 * when the family is indexed, is that the declaration agrees with itself: bounds only on a number,
 * whole numbers on an integer, the minimum not above the maximum, an exclusive flag only on a declared
 * bound, a range that still holds a value, and the default inside it.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParamBounds {

    /** Marks an element that is not declared. A NaN, because no real bound or default can be one. */
    double UNSET = Double.NaN;

    /** What an omitted argument means. */
    double defaultValue() default UNSET;

    /** The smallest value the tool takes as asked, inclusive unless {@link #exclusiveMin()}. */
    double min() default UNSET;

    /** The largest value the tool takes as asked, inclusive unless {@link #exclusiveMax()}. */
    double max() default UNSET;

    /** Whether {@link #min()} itself is refused: advertised as {@code exclusiveMinimum}. */
    boolean exclusiveMin() default false;

    /** Whether {@link #max()} itself is refused: advertised as {@code exclusiveMaximum}. */
    boolean exclusiveMax() default false;
}
