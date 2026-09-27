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
 * The record a tool's {@code structuredContent} is built from. {@link McpSchemaGenerator} turns it
 * into the tool's {@code outputSchema} once, when the family is indexed.
 * <p>
 * The advertised schema then matches what the tool returns on two conditions. The tool must pass an
 * instance of this record to {@link McpToolResult#of}; nothing checks that at runtime, and the tool's
 * tests assert it with {@code McpSchemaConformance}. Jackson must also write the record as it is
 * declared; the generator refuses the Jackson customisations that would break that.
 * <p>
 * A tool that declares one must return {@link McpToolResult} or {@link McpToolOutcome}, and every
 * result it completes with must carry structured content.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpOutputSchema {

    Class<? extends Record> value();
}
