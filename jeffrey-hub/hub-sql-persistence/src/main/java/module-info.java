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
module cafe.jeffrey.hub.persistence.sql {
    requires transitive cafe.jeffrey.hub.persistence.api;
    requires transitive cafe.jeffrey.shared.persistence;
    requires transitive cafe.jeffrey.hub.model;
    requires cafe.jeffrey.shared.common;
    requires java.sql;
    requires spring.jdbc;
    requires flyway.core;
    requires org.slf4j;

    exports cafe.jeffrey.hub.persistence.jdbc;

    // Flyway loads SQL migrations via ClassLoader.getResourceAsStream; Loading of SQL Schemas
    opens db.migration.hub;
}
