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
module cafe.jeffrey.microscope.profile.ai.config {
    requires transitive spring.ai.client.chat;
    requires transitive spring.ai.model;
    requires spring.ai.anthropic;
    requires spring.ai.ollama;
    requires spring.ai.openai;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires cafe.jeffrey.shared.common;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    // Spring AI 2.0.0 exposes these SDK types in its public API (OpenAiSetup/AnthropicSetup
    // return types and ObservationRegistry parameters): named modules must read them explicitly.
    requires micrometer.observation;
    requires openai.java.core;
    requires anthropic.java.core;

    exports cafe.jeffrey.profile.ai.config;
    exports cafe.jeffrey.profile.ai.chat;

    opens cafe.jeffrey.profile.ai.config to spring.core, spring.beans, spring.context;
}
