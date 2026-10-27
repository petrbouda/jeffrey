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
module cafe.jeffrey.microscope.model {
    requires transitive cafe.jeffrey.shared.common;
    requires transitive tools.jackson.core;
    requires transitive tools.jackson.databind;
    requires jdk.jfr;
    exports cafe.jeffrey.microscope.model;
    exports cafe.jeffrey.microscope.model.hub;
    exports cafe.jeffrey.microscope.model.repository;
    exports cafe.jeffrey.microscope.model.repository.matcher;
    exports cafe.jeffrey.microscope.model.time;
    exports cafe.jeffrey.microscope.model.workspace;
    exports cafe.jeffrey.microscope.model.serde;
    exports cafe.jeffrey.microscope.model.settings;
    exports cafe.jeffrey.microscope.model.jfr;
}
