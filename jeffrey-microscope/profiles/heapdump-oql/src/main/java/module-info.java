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
module cafe.jeffrey.microscope.profile.heapdump.oql {
    requires transitive cafe.jeffrey.microscope.profile.heapdump;
    requires cafe.jeffrey.microscope.profile.common;
    requires cafe.jeffrey.shared.common;
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
