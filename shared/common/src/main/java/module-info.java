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
module cafe.jeffrey.shared.common {
    requires jdk.jfr;
    requires transitive org.slf4j;
    requires org.lz4.java;
    requires com.github.f4b6a3.uuid;
    requires transitive tools.jackson.core;
    requires transitive tools.jackson.databind;

    exports cafe.jeffrey.shared.common;
    exports cafe.jeffrey.shared.common.compression;
    exports cafe.jeffrey.shared.common.encryption;
    exports cafe.jeffrey.shared.common.exception;
    exports cafe.jeffrey.shared.common.filesystem;
    exports cafe.jeffrey.shared.common.jfr;
    exports cafe.jeffrey.shared.common.measure;
    exports cafe.jeffrey.shared.common.model;
    exports cafe.jeffrey.shared.common.model.job;
    exports cafe.jeffrey.shared.common.model.repository;
    exports cafe.jeffrey.shared.common.model.repository.matcher;
    exports cafe.jeffrey.shared.common.model.time;
    exports cafe.jeffrey.shared.common.model.workspace;
    exports cafe.jeffrey.shared.common.model.workspace.event;
    exports cafe.jeffrey.shared.common.serde;
    exports cafe.jeffrey.shared.common.settings;
}
