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

module pbouda.jeffrey.shared.common {
    requires transitive tools.jackson.databind;
    requires transitive tools.jackson.core;
    requires com.github.f4b6a3.uuid;
    requires org.lz4.java;
    requires org.slf4j;
    requires jdk.jfr;

    exports pbouda.jeffrey.shared.common;
    exports pbouda.jeffrey.shared.common.compression;
    exports pbouda.jeffrey.shared.common.encryption;
    exports pbouda.jeffrey.shared.common.exception;
    exports pbouda.jeffrey.shared.common.filesystem;
    exports pbouda.jeffrey.shared.common.jfr;
    exports pbouda.jeffrey.shared.common.measure;
    exports pbouda.jeffrey.shared.common.model;
    exports pbouda.jeffrey.shared.common.model.job;
    exports pbouda.jeffrey.shared.common.model.repository;
    exports pbouda.jeffrey.shared.common.model.repository.matcher;
    exports pbouda.jeffrey.shared.common.model.time;
    exports pbouda.jeffrey.shared.common.model.workspace;
    exports pbouda.jeffrey.shared.common.model.workspace.event;
    exports pbouda.jeffrey.shared.common.serde;
    exports pbouda.jeffrey.shared.common.settings;
}
