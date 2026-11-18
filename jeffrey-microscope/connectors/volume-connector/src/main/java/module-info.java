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

/**
 * Microscope's connector to the recording volume: the client interfaces and DTOs every caller uses
 * ({@code api}) and the clients over a volume ({@code volume}). The volume's backends, a local
 * directory and an Azure file share, are modules of their own, so either can be used without the other.
 */
module cafe.jeffrey.microscope.connector.volume {
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.model;
    requires transitive cafe.jeffrey.shared.volume;
    requires org.slf4j;

    exports cafe.jeffrey.microscope.connector.api;
    exports cafe.jeffrey.microscope.connector.api.dto;
    exports cafe.jeffrey.microscope.connector.api.manager;
    exports cafe.jeffrey.microscope.connector.volume;
    exports cafe.jeffrey.microscope.connector.local;
}
