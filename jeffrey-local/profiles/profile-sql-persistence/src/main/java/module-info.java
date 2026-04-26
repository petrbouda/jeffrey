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

module pbouda.jeffrey.profile.sql.persistence {
    requires transitive pbouda.jeffrey.profile.persistence.api;
    requires transitive pbouda.jeffrey.shared.persistence;
    requires transitive pbouda.jeffrey.sql;
    requires transitive pbouda.jeffrey.jfrparser.db;
    requires transitive org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires spring.jdbc;
    requires duckdb.jdbc;
    requires flyway.core;
    requires flyway.database.duckdb;
    requires org.lz4.java;
    requires java.sql;
    requires org.slf4j;
    requires tools.jackson.core;
    requires tools.jackson.databind;

    exports pbouda.jeffrey.provider.profile.sql;
    exports pbouda.jeffrey.provider.profile.sql.repository;
    exports pbouda.jeffrey.provider.profile.calculated;
    exports pbouda.jeffrey.provider.profile.enhancer;
    exports pbouda.jeffrey.provider.profile.query;
    exports pbouda.jeffrey.provider.profile.query.builder;
    exports pbouda.jeffrey.provider.profile.query.timeseries;
    exports pbouda.jeffrey.provider.profile.writer;
}
