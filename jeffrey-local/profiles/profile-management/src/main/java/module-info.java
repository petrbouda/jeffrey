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

module pbouda.jeffrey.profile.management {
    requires transitive pbouda.jeffrey.shared.common;
    requires transitive pbouda.jeffrey.profile.common;
    requires transitive pbouda.jeffrey.local.persistence.api;
    requires transitive pbouda.jeffrey.profile.persistence.api;
    requires transitive pbouda.jeffrey.profile.sql.persistence;
    requires transitive pbouda.jeffrey.recording.storage.api;
    requires transitive pbouda.jeffrey.flamegraph;
    requires transitive pbouda.jeffrey.frameir;
    requires transitive pbouda.jeffrey.timeseries;
    requires transitive pbouda.jeffrey.generator.subsecond;
    requires transitive pbouda.jeffrey.profile.heapdump;
    requires transitive pbouda.jeffrey.profile.guardian;
    requires transitive pbouda.jeffrey.profile.thread;
    requires transitive pbouda.jeffrey.profile.tools;
    requires transitive pbouda.jeffrey.profile.ai.config;
    requires transitive pbouda.jeffrey.profile.ai.oql;
    requires transitive pbouda.jeffrey.profile.ai.mcp.duckdb;
    requires transitive pbouda.jeffrey.profile.ai.mcp.heapdump;
    requires transitive pbouda.jeffrey.jfrparser.jdk;
    requires org.openjdk.jmc.common;
    requires org.openjdk.jmc.flightrecorder;
    requires org.openjdk.jmc.flightrecorder.rules;
    requires org.openjdk.jmc.flightrecorder.rules.jdk;
    requires HdrHistogram;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires spring.beans;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.web;
    requires spring.webmvc;
    requires spring.core;
    requires com.fasterxml.jackson.annotation;
    requires java.sql;
    requires jdk.jfr;
    requires org.slf4j;
    requires tools.jackson.databind;
    requires tools.jackson.core;

    exports pbouda.jeffrey.profile;
    exports pbouda.jeffrey.profile.configuration;
    exports pbouda.jeffrey.profile.feature;
    exports pbouda.jeffrey.profile.feature.checker;
    exports pbouda.jeffrey.profile.manager;
    exports pbouda.jeffrey.profile.manager.action;
    exports pbouda.jeffrey.profile.manager.additional;
    exports pbouda.jeffrey.profile.manager.builder;
    exports pbouda.jeffrey.profile.manager.custom;
    exports pbouda.jeffrey.profile.manager.custom.builder;
    exports pbouda.jeffrey.profile.manager.custom.model.grpc;
    exports pbouda.jeffrey.profile.manager.custom.model.http;
    exports pbouda.jeffrey.profile.manager.custom.model.jdbc.pool;
    exports pbouda.jeffrey.profile.manager.custom.model.jdbc.statement;
    exports pbouda.jeffrey.profile.manager.custom.model.method;
    exports pbouda.jeffrey.profile.manager.model;
    exports pbouda.jeffrey.profile.manager.model.container;
    exports pbouda.jeffrey.profile.manager.model.gc;
    exports pbouda.jeffrey.profile.manager.model.gc.configuration;
    exports pbouda.jeffrey.profile.manager.model.heap;
    exports pbouda.jeffrey.profile.manager.model.thread;
    exports pbouda.jeffrey.profile.manager.registry;
    exports pbouda.jeffrey.profile.model;
    exports pbouda.jeffrey.profile.parser;
    exports pbouda.jeffrey.profile.parser.chunk;
    exports pbouda.jeffrey.profile.parser.data;
    exports pbouda.jeffrey.profile.parser.fields;
    exports pbouda.jeffrey.profile.parser.stacktrace;
    exports pbouda.jeffrey.profile.parser.tag;
    exports pbouda.jeffrey.profile.resources.request;
    exports pbouda.jeffrey.profile.settings;
}
