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
module cafe.jeffrey.local.profile.persistence.jdbc {
    requires transitive cafe.jeffrey.local.profile.persistence.api;
    requires transitive cafe.jeffrey.shared.persistence;
    requires cafe.jeffrey.shared.common;
    requires cafe.jeffrey.shared.sql.builder;
    requires cafe.jeffrey.local.profile.parser.db;
    requires transitive spring.jdbc;
    requires transitive org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires org.lz4.java;
    requires java.sql;
    requires duckdb.jdbc;
    requires flyway.core;
    requires tools.jackson.databind;
    requires org.slf4j;

    exports cafe.jeffrey.provider.profile.jdbc;

    opens db.migration.profile;
}
