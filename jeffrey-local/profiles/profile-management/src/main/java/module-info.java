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
module cafe.jeffrey.local.profile.management {
    // Intra-monorepo
    requires transitive cafe.jeffrey.local.profile.common;
    requires transitive cafe.jeffrey.local.persistence.api;
    requires transitive cafe.jeffrey.local.profile.persistence.api;
    requires cafe.jeffrey.local.profile.persistence.jdbc;
    requires cafe.jeffrey.local.profile.flamegraph;
    requires cafe.jeffrey.local.profile.heapdump;
    requires cafe.jeffrey.local.profile.ai.oql;
    requires cafe.jeffrey.local.profile.ai.mcp.duckdb;
    requires cafe.jeffrey.local.profile.ai.mcp.heapdump;
    requires cafe.jeffrey.local.profile.ai.config;
    requires cafe.jeffrey.local.profile.subsecond;
    requires cafe.jeffrey.local.profile.guardian;
    requires cafe.jeffrey.local.profile.thread;
    requires cafe.jeffrey.local.profile.tools;
    requires cafe.jeffrey.local.profile.timeseries;
    requires cafe.jeffrey.local.profile.frame.ir;
    requires cafe.jeffrey.local.profile.parser.jdk;
    requires cafe.jeffrey.local.profile.parser.db;
    requires cafe.jeffrey.shared.common;
    requires cafe.jeffrey.shared.persistence;
    requires cafe.jeffrey.shared.storage.recording.api;
    requires cafe.jeffrey.jfr.events;

    // 3rd-party
    requires org.openjdk.jmc.common;
    requires org.openjdk.jmc.flightrecorder;
    requires org.openjdk.jmc.flightrecorder.rules;
    requires org.openjdk.jmc.flightrecorder.rules.jdk;
    requires HdrHistogram;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires spring.web;
    requires spring.webmvc;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires tools.jackson.databind;
    requires tools.jackson.core;
    requires com.google.protobuf;
    requires java.sql;
    requires jdk.jfr;
    requires org.slf4j;

    exports cafe.jeffrey.profile;
    exports cafe.jeffrey.profile.configuration;
    exports cafe.jeffrey.profile.feature;
    exports cafe.jeffrey.profile.feature.checker;
    exports cafe.jeffrey.profile.manager;
    exports cafe.jeffrey.profile.manager.action;
    exports cafe.jeffrey.profile.manager.additional;
    exports cafe.jeffrey.profile.manager.builder;
    exports cafe.jeffrey.profile.manager.custom;
    exports cafe.jeffrey.profile.manager.custom.builder;
    exports cafe.jeffrey.profile.manager.custom.model.grpc;
    exports cafe.jeffrey.profile.manager.custom.model.http;
    exports cafe.jeffrey.profile.manager.custom.model.jdbc.pool;
    exports cafe.jeffrey.profile.manager.custom.model.jdbc.statement;
    exports cafe.jeffrey.profile.manager.custom.model.method;
    exports cafe.jeffrey.profile.manager.model;
    exports cafe.jeffrey.profile.manager.model.container;
    exports cafe.jeffrey.profile.manager.model.gc;
    exports cafe.jeffrey.profile.manager.model.gc.configuration;
    exports cafe.jeffrey.profile.manager.model.heap;
    exports cafe.jeffrey.profile.manager.model.thread;
    exports cafe.jeffrey.profile.manager.registry;
    exports cafe.jeffrey.profile.model;
    exports cafe.jeffrey.profile.parser;
    exports cafe.jeffrey.profile.parser.chunk;
    exports cafe.jeffrey.profile.parser.data;
    exports cafe.jeffrey.profile.parser.fields;
    exports cafe.jeffrey.profile.parser.stacktrace;
    exports cafe.jeffrey.profile.parser.tag;
    exports cafe.jeffrey.profile.resources.request;
    exports cafe.jeffrey.profile.settings;

    // Spring + Jackson reflection — opens broadly, can be tightened later
    opens cafe.jeffrey.profile to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.profile.configuration to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.profile.feature to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.profile.manager to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.profile.resources.request to tools.jackson.databind;
    opens cafe.jeffrey.profile.model to tools.jackson.databind;
    opens cafe.jeffrey.profile.settings to tools.jackson.databind;
}
