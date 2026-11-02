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

package cafe.jeffrey.microscope.core.web.controllers.connector;

import cafe.jeffrey.microscope.model.connector.AzureFilesAddress;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorManager;
import cafe.jeffrey.microscope.core.web.dto.response.CurrentConnectorResponse;
import cafe.jeffrey.microscope.core.web.dto.response.ConnectorResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one configured connector, read-only: configuration ({@code jeffrey.microscope.connector.*}) is the
 * only place it is declared, so there is nothing here to connect, change or disconnect one.
 */
@RestController
@RequestMapping("/api/internal/connector")
public class ConnectorController {

    private static final String LOCATION_SEPARATOR = "/";

    private final ConnectorManager connectorManager;

    public ConnectorController(ConnectorManager connectorManager) {
        this.connectorManager = connectorManager;
    }

    @GetMapping
    public CurrentConnectorResponse current() {
        return new CurrentConnectorResponse(connectorManager.info().map(ConnectorController::toResponse).orElse(null));
    }

    private static ConnectorResponse toResponse(ConnectorInfo connector) {
        AzureFilesAddress share = connector.address();
        return new ConnectorResponse(connector.name(), location(share), share.readOnly());
    }

    private static String location(AzureFilesAddress azure) {
        String share = azure.endpoint() + LOCATION_SEPARATOR + azure.share();
        return azure.rootPath().isEmpty() ? share : share + LOCATION_SEPARATOR + azure.rootPath();
    }
}
