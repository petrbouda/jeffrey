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
// protobuf-java is an automatic module (its jar carries no module-info), so javac warns that
// re-exporting it is fragile. Re-exported deliberately: the exported flamegraph API returns its messages.
@SuppressWarnings("requires-transitive-automatic")
module cafe.jeffrey.microscope.profile.management {
    requires transitive cafe.jeffrey.microscope.common;
    // Intra-monorepo
    requires transitive cafe.jeffrey.microscope.profile.common;
    requires cafe.jeffrey.microscope.notification;
    requires transitive cafe.jeffrey.microscope.persistence;
    requires transitive cafe.jeffrey.microscope.profile.persistence;
    requires transitive cafe.jeffrey.microscope.profile.heapdump;
    requires transitive cafe.jeffrey.microscope.profile.heapdump.oql;
    requires cafe.jeffrey.microscope.profile.parser;
    requires transitive cafe.jeffrey.microscope.model;
    requires cafe.jeffrey.microscope.duckdb;
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
    requires transitive org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires tools.jackson.databind;
    requires tools.jackson.core;
    requires transitive com.google.protobuf;
    requires java.sql;
    requires jdk.jfr;
    requires org.slf4j;

    exports cafe.jeffrey.microscope.profile.management;
    exports cafe.jeffrey.microscope.profile.management.configuration;
    exports cafe.jeffrey.microscope.profile.management.feature;
    exports cafe.jeffrey.microscope.profile.management.feature.checker;
    exports cafe.jeffrey.microscope.profile.management.manager;
    exports cafe.jeffrey.microscope.profile.management.manager.action;
    exports cafe.jeffrey.microscope.profile.management.manager.builder;
    exports cafe.jeffrey.microscope.profile.management.manager.model.container;
    exports cafe.jeffrey.microscope.profile.management.manager.model.span;
    exports cafe.jeffrey.microscope.profile.management.manager.registry;
    exports cafe.jeffrey.microscope.profile.management.model;
    exports cafe.jeffrey.microscope.profile.management.panel;
    exports cafe.jeffrey.microscope.profile.management.ingest;
    exports cafe.jeffrey.microscope.profile.management.perfcounters;
    exports cafe.jeffrey.microscope.profile.management.ingest.data;
    exports cafe.jeffrey.microscope.profile.management.ingest.fields;
    exports cafe.jeffrey.microscope.profile.management.ingest.stacktrace;
    exports cafe.jeffrey.microscope.profile.management.ingest.tag;
    exports cafe.jeffrey.microscope.profile.management.resources.request;
    exports cafe.jeffrey.microscope.profile.management.settings;
    exports cafe.jeffrey.microscope.profile.management.tools.collapse;

    // Features, one package tree each
    exports cafe.jeffrey.microscope.profile.management.flamegraph;
    exports cafe.jeffrey.microscope.profile.management.flamegraph.api;
    exports cafe.jeffrey.microscope.profile.management.flamegraph.code;
    exports cafe.jeffrey.microscope.profile.management.flamegraph.diff;
    exports cafe.jeffrey.microscope.profile.management.flamegraph.export;
    exports cafe.jeffrey.microscope.profile.management.flamegraph.provider;
    exports cafe.jeffrey.microscope.profile.management.frameir;
    exports cafe.jeffrey.microscope.profile.management.frameir.frame;
    exports cafe.jeffrey.microscope.profile.management.timeseries;
    exports cafe.jeffrey.microscope.profile.management.subsecond;
    exports cafe.jeffrey.microscope.profile.management.subsecond.api;
    exports cafe.jeffrey.microscope.profile.management.thread;
    exports cafe.jeffrey.microscope.profile.management.manager.thread;
    exports cafe.jeffrey.microscope.profile.management.manager.thread.builder;
    exports cafe.jeffrey.microscope.profile.management.manager.model.thread;
    exports cafe.jeffrey.microscope.profile.management.manager.model.thread.dump;
    exports cafe.jeffrey.microscope.profile.management.manager.model.virtualthread;
    exports cafe.jeffrey.microscope.profile.management.manager.gc;
    exports cafe.jeffrey.microscope.profile.management.manager.gc.builder;
    exports cafe.jeffrey.microscope.profile.management.manager.model.gc;
    exports cafe.jeffrey.microscope.profile.management.manager.model.gc.configuration;
    exports cafe.jeffrey.microscope.profile.management.manager.model.gc.finalizer;
    exports cafe.jeffrey.microscope.profile.management.manager.model.gc.g1;
    exports cafe.jeffrey.microscope.profile.management.manager.model.gc.tables;
    exports cafe.jeffrey.microscope.profile.management.manager.model.gc.tuning;
    exports cafe.jeffrey.microscope.profile.management.manager.model.gc.zgc;
    exports cafe.jeffrey.microscope.profile.management.manager.memory;
    exports cafe.jeffrey.microscope.profile.management.manager.model.allocation;
    exports cafe.jeffrey.microscope.profile.management.manager.model.heap;
    exports cafe.jeffrey.microscope.profile.management.manager.model.leak;
    exports cafe.jeffrey.microscope.profile.management.manager.model.nativememory;
    exports cafe.jeffrey.microscope.profile.management.manager.model.nmt;
    exports cafe.jeffrey.microscope.profile.management.manager.custom;
    exports cafe.jeffrey.microscope.profile.management.manager.custom.builder;
    exports cafe.jeffrey.microscope.profile.management.manager.custom.model.grpc;
    exports cafe.jeffrey.microscope.profile.management.manager.custom.model.http;
    exports cafe.jeffrey.microscope.profile.management.manager.custom.model.jdbc.pool;
    exports cafe.jeffrey.microscope.profile.management.manager.custom.model.jdbc.statement;
    exports cafe.jeffrey.microscope.profile.management.manager.custom.model.method;
    exports cafe.jeffrey.microscope.profile.management.manager.heapdump;
    exports cafe.jeffrey.microscope.profile.management.manager.heapdump.analysis;

    // Spring + Jackson reflection — opens broadly, can be tightened later
    opens cafe.jeffrey.microscope.profile.management to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.microscope.profile.management.configuration to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.microscope.profile.management.feature to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.microscope.profile.management.manager to spring.core, spring.beans, spring.context, tools.jackson.databind;
    opens cafe.jeffrey.microscope.profile.management.resources.request to tools.jackson.databind;
    opens cafe.jeffrey.microscope.profile.management.model to tools.jackson.databind;
    opens cafe.jeffrey.microscope.profile.management.settings to tools.jackson.databind;
}
