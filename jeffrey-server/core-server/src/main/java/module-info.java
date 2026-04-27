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
module cafe.jeffrey.server.core {
    // Intra-monorepo
    requires cafe.jeffrey.shared.common;
    requires cafe.jeffrey.shared.folderqueue;
    requires cafe.jeffrey.shared.persistentqueue;
    requires cafe.jeffrey.shared.server.api;
    requires cafe.jeffrey.server.persistence.api;
    requires cafe.jeffrey.server.persistence.sql;
    requires cafe.jeffrey.jfr.events;

    // Spring Boot 4 / Spring 7
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.boot.tomcat;
    requires spring.boot.web.server;
    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires spring.web;
    requires spring.webmvc;
    requires org.apache.tomcat.embed.core;
    requires org.apache.commons.logging;
    requires org.jspecify;

    // Jackson 3
    requires tools.jackson.databind;
    requires tools.jackson.core;

    // gRPC server runtime
    requires io.grpc;
    requires io.grpc.stub;
    requires io.grpc.netty;
    requires io.grpc.services;

    // Logging
    requires org.slf4j;

    // JDK
    requires java.sql;
    requires jdk.jfr;

    // Spring Boot reflective access — entry-point + WebMvcConfigurer + @SpringBootApplication scan
    opens cafe.jeffrey.server.core to spring.core, spring.beans, spring.context;

    // @Configuration classes are CGLIB-proxied at runtime by Spring
    opens cafe.jeffrey.server.core.configuration to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.server.core.configuration.properties to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.server.core.configuration.workspace to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.server.core.grpc to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.server.core.web to spring.core, spring.beans, spring.context;

    // Spring MVC reflectively dispatches to @RestController handler methods
    opens cafe.jeffrey.server.core.web.controllers to spring.core, spring.beans, spring.context, spring.web, tools.jackson.databind;

    // Jackson 3 record introspection for JSON serialization
    opens cafe.jeffrey.server.core.resources.response to tools.jackson.databind;
}
