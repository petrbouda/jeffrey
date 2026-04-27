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
module cafe.jeffrey.local.profile.ai.mcp.duckdb {
    requires transitive cafe.jeffrey.local.profile.persistence.api;
    requires cafe.jeffrey.shared.common;
    requires cafe.jeffrey.shared.persistence;
    requires cafe.jeffrey.local.profile.common;
    requires transitive spring.ai.client.chat;
    requires spring.ai.model;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires java.sql;
    requires org.slf4j;

    exports cafe.jeffrey.profile.ai.oql.duckdb;
    exports cafe.jeffrey.profile.ai.oql.duckdb.config;
    exports cafe.jeffrey.profile.ai.oql.duckdb.model;
    exports cafe.jeffrey.profile.ai.oql.duckdb.prompt;
    exports cafe.jeffrey.profile.ai.oql.duckdb.service;
    exports cafe.jeffrey.profile.ai.oql.duckdb.tools;

    opens cafe.jeffrey.profile.ai.oql.duckdb.config to spring.core, spring.beans, spring.context;
}
