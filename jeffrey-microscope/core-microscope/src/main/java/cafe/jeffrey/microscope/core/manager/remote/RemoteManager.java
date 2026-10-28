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

package cafe.jeffrey.microscope.core.manager.remote;

import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;

import java.util.Optional;

/**
 * The one remote this Microscope reaches — the recording volume on an Azure file share — exactly
 * as configuration declares it ({@code jeffrey.microscope.remote.*}). It is read
 * once at startup and never connected, changed or disconnected while Microscope runs: nothing
 * stores it, and the UI only shows it.
 */
public class RemoteManager {

    private static final String NONE_CONFIGURED =
            "No remote is configured. Declare one under jeffrey.microscope.remote.* and restart.";

    private final Optional<RemoteInfo> remote;
    private final RemoteConnection.Factory connectionFactory;

    /**
     * @param remote the configured remote; empty when configuration declares none
     */
    public RemoteManager(Optional<RemoteInfo> remote, RemoteConnection.Factory connectionFactory) {
        this.remote = remote;
        this.connectionFactory = connectionFactory;
    }

    /** What configuration declares, without opening a connection to it. */
    public Optional<RemoteInfo> info() {
        return remote;
    }

    /** The configured remote, or empty when none is configured. */
    public Optional<RemoteConnection> current() {
        return remote.map(connectionFactory);
    }

    /**
     * The configured remote.
     *
     * @throws JeffreyClientException not found, naming how to configure one
     */
    public RemoteConnection require() {
        return current().orElseThrow(() -> Exceptions.resourceNotFound(NONE_CONFIGURED));
    }
}
