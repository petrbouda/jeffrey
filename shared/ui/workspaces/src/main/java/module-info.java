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
module cafe.jeffrey.shared.ui.workspace {
    requires transitive cafe.jeffrey.hub.client;
    requires transitive cafe.jeffrey.recordings.core;
    requires transitive cafe.jeffrey.microscope.persistence.api;
    requires transitive cafe.jeffrey.shared.common;
    requires spring.web;
    requires spring.webmvc;
    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires tools.jackson.databind;
    requires org.slf4j;

    exports cafe.jeffrey.shared.ui.workspace.controller;
    exports cafe.jeffrey.shared.ui.workspace.bridge;
    exports cafe.jeffrey.shared.ui.workspace.dto;
    exports cafe.jeffrey.shared.ui.workspace.request;
    exports cafe.jeffrey.shared.ui.workspace.config;
}
