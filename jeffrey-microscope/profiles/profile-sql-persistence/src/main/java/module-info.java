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
// spring.jdbc is an automatic module (its jar carries no module-info), so javac warns that
// re-exporting it is fragile. Re-exported deliberately: the exported repositories take RowMapper
// and MapSqlParameterSource in their own signatures.
@SuppressWarnings("requires-transitive-automatic")
module cafe.jeffrey.microscope.profile.persistence.jdbc {
    requires transitive cafe.jeffrey.microscope.profile.persistence.api;
    requires transitive cafe.jeffrey.shared.persistence;
    requires cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.model;
    requires cafe.jeffrey.shared.sql.builder;
    requires cafe.jeffrey.microscope.profile.parser.api;
    requires transitive cafe.jeffrey.microscope.profile.common;
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
