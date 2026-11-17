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
    // Intra-monorepo
    requires transitive cafe.jeffrey.microscope.profile.common;
    requires cafe.jeffrey.shared.notifications;
    requires transitive cafe.jeffrey.microscope.persistence;
    requires transitive cafe.jeffrey.microscope.profile.persistence;
    requires transitive cafe.jeffrey.microscope.profile.heapdump;
    requires transitive cafe.jeffrey.microscope.profile.heapdump.oql;
    requires cafe.jeffrey.microscope.profile.parser;
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.model;
    requires cafe.jeffrey.shared.persistence;
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

    exports cafe.jeffrey.profile;
    exports cafe.jeffrey.profile.configuration;
    exports cafe.jeffrey.profile.feature;
    exports cafe.jeffrey.profile.feature.checker;
    exports cafe.jeffrey.profile.manager;
    exports cafe.jeffrey.profile.manager.action;
    exports cafe.jeffrey.profile.manager.builder;
    exports cafe.jeffrey.profile.manager.model.container;
    exports cafe.jeffrey.profile.manager.model.span;
    exports cafe.jeffrey.profile.manager.registry;
    exports cafe.jeffrey.profile.model;
    exports cafe.jeffrey.profile.panel;
    exports cafe.jeffrey.profile.parser;
    exports cafe.jeffrey.profile.perfcounters;
    exports cafe.jeffrey.profile.parser.data;
    exports cafe.jeffrey.profile.parser.fields;
    exports cafe.jeffrey.profile.parser.stacktrace;
    exports cafe.jeffrey.profile.parser.tag;
    exports cafe.jeffrey.profile.resources.request;
    exports cafe.jeffrey.profile.settings;
    exports cafe.jeffrey.profile.tools.collapse;

    // Features, one package tree each
    exports cafe.jeffrey.flamegraph;
    exports cafe.jeffrey.flamegraph.api;
    exports cafe.jeffrey.flamegraph.code;
    exports cafe.jeffrey.flamegraph.diff;
    exports cafe.jeffrey.flamegraph.export;
    exports cafe.jeffrey.flamegraph.provider;
    exports cafe.jeffrey.frameir;
    exports cafe.jeffrey.frameir.frame;
    exports cafe.jeffrey.timeseries;
    exports cafe.jeffrey.subsecond.db;
    exports cafe.jeffrey.subsecond.db.api;
    exports cafe.jeffrey.profile.thread;
    exports cafe.jeffrey.profile.manager.thread;
    exports cafe.jeffrey.profile.manager.thread.builder;
    exports cafe.jeffrey.profile.manager.model.thread;
    exports cafe.jeffrey.profile.manager.model.thread.dump;
    exports cafe.jeffrey.profile.manager.model.virtualthread;
    exports cafe.jeffrey.profile.manager.gc;
    exports cafe.jeffrey.profile.manager.gc.builder;
    exports cafe.jeffrey.profile.manager.model.gc;
    exports cafe.jeffrey.profile.manager.model.gc.configuration;
    exports cafe.jeffrey.profile.manager.model.gc.finalizer;
    exports cafe.jeffrey.profile.manager.model.gc.g1;
    exports cafe.jeffrey.profile.manager.model.gc.tables;
    exports cafe.jeffrey.profile.manager.model.gc.tuning;
    exports cafe.jeffrey.profile.manager.model.gc.zgc;
    exports cafe.jeffrey.profile.manager.memory;
    exports cafe.jeffrey.profile.manager.model.allocation;
    exports cafe.jeffrey.profile.manager.model.heap;
    exports cafe.jeffrey.profile.manager.model.leak;
    exports cafe.jeffrey.profile.manager.model.nativememory;
    exports cafe.jeffrey.profile.manager.model.nmt;
    exports cafe.jeffrey.profile.manager.custom;
    exports cafe.jeffrey.profile.manager.custom.builder;
    exports cafe.jeffrey.profile.manager.custom.model.grpc;
    exports cafe.jeffrey.profile.manager.custom.model.http;
    exports cafe.jeffrey.profile.manager.custom.model.jdbc.pool;
    exports cafe.jeffrey.profile.manager.custom.model.jdbc.statement;
    exports cafe.jeffrey.profile.manager.custom.model.method;
    exports cafe.jeffrey.profile.manager.heapdump;
    exports cafe.jeffrey.profile.manager.heapdump.analysis;

    // Spring + Jackson reflection — opens broadly, can be tightened later
    opens cafe.jeffrey.profile to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.profile.configuration to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.profile.feature to spring.core, spring.beans, spring.context;
    opens cafe.jeffrey.profile.manager to spring.core, spring.beans, spring.context, tools.jackson.databind;
    opens cafe.jeffrey.profile.resources.request to tools.jackson.databind;
    opens cafe.jeffrey.profile.model to tools.jackson.databind;
    opens cafe.jeffrey.profile.settings to tools.jackson.databind;
}
