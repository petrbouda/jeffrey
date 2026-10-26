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
module cafe.jeffrey.shared.ui.remote {
    requires transitive cafe.jeffrey.microscope.remote.client;
    requires transitive cafe.jeffrey.recordings.core;
    requires transitive cafe.jeffrey.microscope.persistence.api;
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.model;
    requires spring.web;
    requires spring.webmvc;
    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires tools.jackson.databind;
    requires org.slf4j;

    exports cafe.jeffrey.shared.ui.remote.controller;
    exports cafe.jeffrey.shared.ui.remote.bridge;
    exports cafe.jeffrey.shared.ui.remote.dto;
    exports cafe.jeffrey.shared.ui.remote.request;
    exports cafe.jeffrey.shared.ui.remote.config;
}
