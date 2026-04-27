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
module cafe.jeffrey.local.core {
    // Intra-monorepo
    requires cafe.jeffrey.shared.common;
    requires cafe.jeffrey.shared.persistence;
    requires cafe.jeffrey.shared.storage.recording.api;
    requires cafe.jeffrey.shared.storage.recording.filesystem;
    requires cafe.jeffrey.shared.server.api;
    requires cafe.jeffrey.local.persistence.api;
    requires cafe.jeffrey.local.persistence.jdbc;
    requires cafe.jeffrey.local.grpc.client;
    requires cafe.jeffrey.local.profile.management;
    requires cafe.jeffrey.local.profile.persistence.api;
    requires cafe.jeffrey.local.profile.persistence.jdbc;
    requires cafe.jeffrey.local.profile.ai.oql;
    requires cafe.jeffrey.local.profile.ai.mcp.duckdb;
    requires cafe.jeffrey.local.profile.ai.mcp.heapdump;
    requires cafe.jeffrey.local.profile.timeseries;
    requires cafe.jeffrey.local.profile.guardian;
    requires cafe.jeffrey.local.profile.thread;
    requires cafe.jeffrey.local.profile.tools;
    requires cafe.jeffrey.local.profile.heapdump;
    requires cafe.jeffrey.jfr.events;

    // Spring Boot 4 / Spring 7
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.boot.tomcat;
    requires spring.boot.web.server;
    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires org.apache.commons.logging;
    requires org.jspecify;
    requires spring.web;
    requires spring.webmvc;
    requires org.apache.tomcat.embed.core;

    // Jackson 3
    requires tools.jackson.databind;
    requires tools.jackson.core;

    // Other 3rd-party
    requires typesafe.config;
    requires net.bytebuddy;
    requires org.slf4j;
    requires java.sql;
    requires java.net.http;
    requires jdk.jfr;

    // Spring Boot reflective access
    opens cafe.jeffrey.local.core to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.local.core.configuration to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.local.core.web to spring.core, spring.beans, spring.context, spring.web;
    opens cafe.jeffrey.local.core.web.controllers to spring.core, spring.beans, spring.context, spring.web, tools.jackson.databind;
    opens cafe.jeffrey.local.core.web.controllers.profile to spring.core, spring.beans, spring.context, spring.web, tools.jackson.databind;

    // Jackson record/DTO serialization
    opens cafe.jeffrey.local.core.resources.request to tools.jackson.databind;
    opens cafe.jeffrey.local.core.resources.response to tools.jackson.databind;
    opens cafe.jeffrey.local.core.resources.workspace to tools.jackson.databind;
}
