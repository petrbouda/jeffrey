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

module cafe.jeffrey.microscope.profile.mcp {
    requires cafe.jeffrey.shared.common;
    requires cafe.jeffrey.jfr.events;

    requires spring.ai.model;
    requires spring.web;
    requires tools.jackson.databind;
    requires org.slf4j;

    exports cafe.jeffrey.profile.mcp;
    exports cafe.jeffrey.profile.mcp.finding;

    opens cafe.jeffrey.profile.mcp to tools.jackson.databind;
}
