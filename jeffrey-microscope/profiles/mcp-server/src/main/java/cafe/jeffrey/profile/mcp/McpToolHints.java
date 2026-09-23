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
