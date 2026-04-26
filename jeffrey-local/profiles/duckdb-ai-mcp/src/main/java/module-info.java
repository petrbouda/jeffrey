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

module pbouda.jeffrey.profile.ai.mcp.duckdb {
    requires transitive pbouda.jeffrey.shared.common;
    requires transitive pbouda.jeffrey.profile.common;
    requires transitive pbouda.jeffrey.profile.persistence.api;
    requires transitive pbouda.jeffrey.shared.persistence;
    requires spring.ai.client.chat;
    requires spring.ai.model;
    requires spring.beans;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires com.fasterxml.jackson.annotation;
    requires java.sql;
    requires org.slf4j;

    exports pbouda.jeffrey.profile.ai.mcp;
    exports pbouda.jeffrey.profile.ai.mcp.config;
    exports pbouda.jeffrey.profile.ai.mcp.model;
    exports pbouda.jeffrey.profile.ai.mcp.prompt;
    exports pbouda.jeffrey.profile.ai.mcp.service;
    exports pbouda.jeffrey.profile.ai.mcp.tools;
}
