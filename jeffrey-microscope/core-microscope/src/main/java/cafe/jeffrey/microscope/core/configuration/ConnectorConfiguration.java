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
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredConnectorProperties;
import cafe.jeffrey.microscope.core.manager.ProfilesManager;
import cafe.jeffrey.microscope.core.manager.project.ConnectorProjectManagerFactory;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.connector.AzureFilesClientsFactory;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorConnection;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorManager;
import cafe.jeffrey.microscope.core.manager.workspace.ConnectorWorkspaceManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManagerFactory;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcWorkspaceRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;


@Configuration
@Import(AppConfiguration.class)
@EnableConfigurationProperties(ConfiguredConnectorProperties.class)
public class ConnectorConfiguration {

    /** The declaration read once, so an unusable one is reported once rather than per bean. */
    @Bean
    public ConfiguredConnectorProperties.Declared declaredConnector(ConfiguredConnectorProperties configuredConnector) {
        return configuredConnector.read();
    }

    /** The share's clients. */
    @Bean
    public AzureFilesClientsFactory azureFilesClientsFactory(ConfiguredConnectorProperties.Declared declaredConnector) {
        return new AzureFilesClientsFactory(declaredConnector.sasToken());
    }

    @Bean
    public ConnectorProjectManagerFactory connectorProjectManagerFactory(
            MicroscopeJeffreyDirs jeffreyDirs,
            ProfilesManager.Factory profilesManagerFactory,
            RecordingsManager recordingsManager) {

        return new ConnectorProjectManagerFactory(jeffreyDirs, profilesManagerFactory, recordingsManager);
    }

    @Bean
    public WorkspaceManagerFactory workspaceManagerFactory(
            MicroscopeJeffreyDirs jeffreyDirs,
            MicroscopeCorePersistenceProvider persistenceProvider,
            ConnectorProjectManagerFactory connectorProjectManagerFactory) {

        return (connector, workspaceInfo, clients) -> new ConnectorWorkspaceManager(
                jeffreyDirs,
                connector,
                workspaceInfo,
                new JdbcWorkspaceRepository(workspaceInfo.id(), persistenceProvider.databaseClientProvider()),
                clients,
                connectorProjectManagerFactory);
    }

    @Bean
    public ConnectorConnection.Factory connectorConnectionFactory(
            AzureFilesClientsFactory azureFilesClientsFactory,
            WorkspaceManagerFactory workspaceManagerFactory,
            ConnectorProjectManagerFactory connectorProjectManagerFactory) {

        return connector -> new ConnectorConnection(
                connector,
                azureFilesClientsFactory.apply(connector.address()),
                workspaceManagerFactory,
                connectorProjectManagerFactory);
    }

    /** The connector configuration declares, read once; nothing connects or disconnects one while Microscope runs. */
    @Bean
    public ConnectorManager connectorManager(
            ConfiguredConnectorProperties.Declared declaredConnector,
            ConnectorConnection.Factory connectorConnectionFactory) {

        return new ConnectorManager(declaredConnector.connector(), connectorConnectionFactory);
    }
}
