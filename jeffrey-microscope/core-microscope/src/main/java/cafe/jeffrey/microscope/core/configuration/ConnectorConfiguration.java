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

package cafe.jeffrey.microscope.core.configuration;

import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.connector.api.spi.ConnectorProviders;
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredConnector;
import cafe.jeffrey.microscope.core.manager.project.ConnectorProjectManagerFactory;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorClientsFactory;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorConnection;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorManager;
import cafe.jeffrey.microscope.core.manager.workspace.ConnectorWorkspaceManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManagerFactory;
import cafe.jeffrey.microscope.core.manager.download.DownloadTaskRegistry;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.time.Clock;


@Configuration
@Import(AppConfiguration.class)
public class ConnectorConfiguration {

    /**
     * The declaration read once, so an unusable one is reported once rather than per bean, over every
     * connector kind on the class path.
     */
    @Bean
    public ConfiguredConnector configuredConnector(Environment environment) {
        return ConfiguredConnector.read(Binder.get(environment), ConnectorProviders.load());
    }

    /** The clients of the configured connector, opened by its kind's provider. */
    @Bean
    public ConnectorClientsFactory connectorClientsFactory(ConfiguredConnector configuredConnector) {
        return new ConnectorClientsFactory(configuredConnector.declared());
    }

    @Bean
    public ConnectorProjectManagerFactory connectorProjectManagerFactory(
            MicroscopeJeffreyDirs jeffreyDirs,
            RecordingsManager recordingsManager) {

        return new ConnectorProjectManagerFactory(jeffreyDirs, recordingsManager);
    }

    @Bean
    public WorkspaceManagerFactory workspaceManagerFactory(ConnectorProjectManagerFactory connectorProjectManagerFactory) {
        return (connector, workspaceInfo, clients) -> new ConnectorWorkspaceManager(
                connector,
                workspaceInfo,
                clients,
                connectorProjectManagerFactory);
    }

    @Bean
    public ConnectorConnection.Factory connectorConnectionFactory(
            ConnectorClientsFactory connectorClientsFactory,
            WorkspaceManagerFactory workspaceManagerFactory,
            ConnectorProjectManagerFactory connectorProjectManagerFactory) {

        return connector -> new ConnectorConnection(
                connector,
                connectorClientsFactory.apply(connector.address()),
                workspaceManagerFactory,
                connectorProjectManagerFactory);
    }

    /** The download tasks the connector's project pages start and follow; closed with the context. */
    @Bean
    public DownloadTaskRegistry downloadTaskRegistry(Clock clock) {
        return new DownloadTaskRegistry(clock);
    }

    /** The connector configuration declares, read once; nothing connects or disconnects one while Microscope runs. */
    @Bean
    public ConnectorManager connectorManager(
            ConfiguredConnector configuredConnector,
            ConnectorConnection.Factory connectorConnectionFactory) {

        return new ConnectorManager(configuredConnector.connector(), connectorConnectionFactory);
    }
}
