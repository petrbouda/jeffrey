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
// spring.jdbc and duckdb.jdbc are automatic modules (their jars carry no module-info), so javac
// warns that re-exporting them ties us to a name derived from a file name. Re-exported deliberately:
// DatabaseClient hands back RowMapper and SqlParameterSource, and the DuckDB driver types travel
// with the DataSource, so every consumer of this module reads them too.
@SuppressWarnings("requires-transitive-automatic")
module cafe.jeffrey.shared.persistence {
    requires transitive java.sql;
    requires transitive duckdb.jdbc;
    requires jdk.jfr;
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.shared.sql.builder;
    requires cafe.jeffrey.jfr.events;
    requires transitive com.zaxxer.hikari;
    requires transitive spring.jdbc;
    requires spring.tx;
    requires tools.jackson.databind;

    exports cafe.jeffrey.shared.persistence;
    exports cafe.jeffrey.shared.persistence.client;
    exports cafe.jeffrey.shared.persistence.metrics;

    uses java.sql.Driver;
}
