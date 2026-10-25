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

package cafe.jeffrey.microscope.core.initializer;

import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredRemoteProperties;
import cafe.jeffrey.microscope.persistence.api.RemoteRepository;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/**
 * Makes the stored connection match {@code jeffrey.microscope.remote.*} at startup.
 * <p>
 * Runs as a bean init method rather than an {@code ApplicationRunner}: Spring Boot invokes runners
 * after the context has finished refreshing, by which point the HTTP connector is already
 * accepting requests and the UI could observe a half-reconciled connection.
 * <p>
 * The remote is not contacted here. A declared remote is stored whether or not it answers, so one
 * that starts after Microscope — the normal case in a compose file or a pod — works as soon as it
 * comes up, instead of being skipped until the next restart.
 */
public class ConfiguredRemoteReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguredRemoteReconciler.class);

    private final RemoteRepository repository;
    private final RemoteClients.Factory clientsFactory;
    private final ConfiguredRemoteProperties properties;
    private final Clock clock;

    public ConfiguredRemoteReconciler(
            RemoteRepository repository,
            RemoteClients.Factory clientsFactory,
            ConfiguredRemoteProperties properties,
            Clock clock) {

        this.repository = repository;
        this.clientsFactory = clientsFactory;
        this.properties = properties;
        this.clock = clock;
    }

    public void reconcile() {
        switch (RemoteReconciliation.of(properties.resolve(), repository.find(), clock.instant())) {
            case RemoteReconciliation.Keep _ -> LOG.debug("The stored remote already matches the configuration");
            case RemoteReconciliation.Save save -> {
                repository.save(save.target());
                save.previous()
                        .filter(previous -> !previous.address().equals(save.target().address()))
                        .ifPresent(previous -> clientsFactory.evict(previous.address()));
                LOG.info("Connected the remote declared in configuration: name={} address={}",
                        save.target().name(), save.target().address());
            }
            case RemoteReconciliation.Disconnect disconnect -> {
                repository.delete();
                clientsFactory.evict(disconnect.previous().address());
                LOG.info("Disconnected the remote no longer declared in configuration: name={} address={}",
                        disconnect.previous().name(), disconnect.previous().address());
            }
        }
    }
}
