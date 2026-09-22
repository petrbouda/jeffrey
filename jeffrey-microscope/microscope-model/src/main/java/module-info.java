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
module cafe.jeffrey.microscope.model {
    requires transitive cafe.jeffrey.shared.common;
    requires transitive tools.jackson.core;
    requires transitive tools.jackson.databind;
    requires jdk.jfr;
    exports cafe.jeffrey.microscope.model;
    exports cafe.jeffrey.microscope.model.config;
    exports cafe.jeffrey.microscope.model.hub;
    exports cafe.jeffrey.microscope.model.repository;
    exports cafe.jeffrey.microscope.model.repository.matcher;
    exports cafe.jeffrey.microscope.model.time;
    exports cafe.jeffrey.microscope.model.workspace;
    exports cafe.jeffrey.microscope.model.serde;
    exports cafe.jeffrey.microscope.model.settings;
    exports cafe.jeffrey.microscope.model.jfr;
}
