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
 * What Microscope asks of a connector, whatever answers it: the client interfaces and DTOs every caller
 * uses ({@code api}, {@code api.dto}), the managers over them ({@code api.manager}), and the connector
 * kinds as providers ({@code api.spi}), found here by {@link java.util.ServiceLoader}. It knows no
 * storage: the recording volume's clients and its local kind are {@code volume-connector}, the Azure
 * file share is {@code azure-files-connector}.
 */
module cafe.jeffrey.microscope.connector.api {
    requires transitive cafe.jeffrey.microscope.common;

    exports cafe.jeffrey.microscope.connector.api;
    exports cafe.jeffrey.microscope.connector.api.dto;
    exports cafe.jeffrey.microscope.connector.api.manager;
    exports cafe.jeffrey.microscope.connector.api.spi;

    uses cafe.jeffrey.microscope.connector.api.spi.ConnectorProvider;
}
