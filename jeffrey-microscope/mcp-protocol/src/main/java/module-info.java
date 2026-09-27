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

/**
 * The MCP 2026-07-28 protocol and nothing else: Jackson for the wire, SLF4J for the log. No Jeffrey
 * type and no Spring — {@code ModuleBoundaryTest} fails on either.
 */
module cafe.jeffrey.microscope.mcp.protocol {
    requires transitive tools.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires org.slf4j;

    exports cafe.jeffrey.microscope.mcp.protocol;

    opens cafe.jeffrey.microscope.mcp.protocol to tools.jackson.databind;
}
