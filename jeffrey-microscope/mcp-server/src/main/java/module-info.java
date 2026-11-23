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
 * Jeffrey's MCP module: the MCP {@code 2026-07-28} protocol types and SPI (tool/result/outcome records,
 * closed schema tooling, cursor, extensions), and the {@code @Tool} adapter over them — reflection,
 * profile scoping, the tool hints and the finding convention.
 */
module cafe.jeffrey.microscope.mcp.server {
    requires transitive cafe.jeffrey.microscope.common;
    requires cafe.jeffrey.jfr.events;

    requires spring.ai.model;
    requires spring.web;
    requires transitive tools.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires org.slf4j;

    exports cafe.jeffrey.microscope.mcp.server;
    exports cafe.jeffrey.microscope.mcp.server.finding;

    opens cafe.jeffrey.microscope.mcp.server to tools.jackson.databind;
}
