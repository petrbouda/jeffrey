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

import tools.jackson.databind.node.ObjectNode;

/**
 * Answers one method of the envelope's method table. A handler receives a request that has already
 * passed {@link McpRequestValidator}, resolves only the providers its method needs, and returns the
 * finished result — already passed through {@link McpResults}, so it says what kind it is, which server
 * answered and, where the method requires one, how long it may be cached.
 */
@FunctionalInterface
interface McpMethodHandler {

    ObjectNode handle(McpRequestContext request, McpServerFeatures features);
}
