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

package cafe.jeffrey.microscope.core.manager.connector;

import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;

import java.util.Optional;

/**
 * The one connector this Microscope reaches — the recording volume on an Azure file share or in a
 * local directory — exactly
 * as configuration declares it ({@code jeffrey.microscope.connector.*}). It is read
 * once at startup and never connected, changed or disconnected while Microscope runs: nothing
 * stores it, and the UI only shows it.
 */
public class ConnectorManager {

    private static final String NONE_CONFIGURED =
            "No connector is configured. Declare one under jeffrey.microscope.connector.* and restart.";

    private final Optional<ConnectorInfo> connector;
    private final ConnectorConnection.Factory connectionFactory;

    /**
     * @param connector the configured connector; empty when configuration declares none
     */
    public ConnectorManager(Optional<ConnectorInfo> connector, ConnectorConnection.Factory connectionFactory) {
        this.connector = connector;
        this.connectionFactory = connectionFactory;
    }

    /** What configuration declares, without opening a connection to it. */
    public Optional<ConnectorInfo> info() {
        return connector;
    }

    /** The configured connector, or empty when none is configured. */
    public Optional<ConnectorConnection> current() {
        return connector.map(connectionFactory);
    }

    /**
     * The configured connector.
     *
     * @throws JeffreyClientException not found, naming how to configure one
     */
    public ConnectorConnection require() {
        return current().orElseThrow(() -> Exceptions.resourceNotFound(NONE_CONFIGURED));
    }
}
