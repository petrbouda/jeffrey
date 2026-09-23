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
module cafe.jeffrey.microscope.profile.heapdump.oql {
    requires transitive cafe.jeffrey.microscope.profile.heapdump;
    requires cafe.jeffrey.shared.persistence;
    requires org.antlr.antlr4.runtime;
    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires java.sql;
    requires duckdb.jdbc;
    requires org.slf4j;

    exports cafe.jeffrey.profile.heapdump.oql;
    exports cafe.jeffrey.profile.heapdump.oql.ast;
    exports cafe.jeffrey.profile.heapdump.oql.compiler;
    exports cafe.jeffrey.profile.heapdump.oql.config;
    exports cafe.jeffrey.profile.heapdump.oql.executor;
    exports cafe.jeffrey.profile.heapdump.oql.function;
    exports cafe.jeffrey.profile.heapdump.oql.parser;

    opens cafe.jeffrey.profile.heapdump.oql.config to spring.core, spring.beans, spring.context;
}
