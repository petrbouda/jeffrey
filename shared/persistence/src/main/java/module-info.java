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
module cafe.jeffrey.shared.persistence {
    requires transitive java.sql;
    requires jdk.jfr;
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.shared.sql.builder;
    requires cafe.jeffrey.jfr.events;
    requires transitive com.zaxxer.hikari;
    requires transitive spring.jdbc;
    requires tools.jackson.databind;

    exports cafe.jeffrey.shared.persistence;
    exports cafe.jeffrey.shared.persistence.client;
    exports cafe.jeffrey.shared.persistence.metrics;

    uses java.sql.Driver;
}
