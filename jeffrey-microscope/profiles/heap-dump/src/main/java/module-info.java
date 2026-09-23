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
module cafe.jeffrey.microscope.profile.heapdump {
    requires transitive cafe.jeffrey.shared.common;
    requires cafe.jeffrey.shared.notifications;
    requires cafe.jeffrey.microscope.profile.common;
    requires cafe.jeffrey.shared.persistence;
    requires cafe.jeffrey.jfr.events;
    requires jdk.jfr;
    requires java.sql;
    requires duckdb.jdbc;
    requires tools.jackson.databind;
    requires org.slf4j;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires org.lz4.java;

    exports cafe.jeffrey.profile.heapdump.analyzer.heapview;
    exports cafe.jeffrey.profile.heapdump.model;
    exports cafe.jeffrey.profile.heapdump.parser;
    exports cafe.jeffrey.profile.heapdump.view;
    exports cafe.jeffrey.profile.heapdump.persistence;
}
