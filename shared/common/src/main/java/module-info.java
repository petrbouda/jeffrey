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
module cafe.jeffrey.shared.common {
    requires transitive org.slf4j;
    requires org.lz4.java;
    requires com.github.f4b6a3.uuid;
    requires transitive tools.jackson.core;
    requires transitive tools.jackson.databind;

    exports cafe.jeffrey.shared.common;
    exports cafe.jeffrey.shared.common.compression;
    exports cafe.jeffrey.shared.common.exception;
    exports cafe.jeffrey.shared.common.filesystem;
    exports cafe.jeffrey.shared.common.measure;
    exports cafe.jeffrey.shared.common.model;
    exports cafe.jeffrey.shared.common.model.repository;
}
