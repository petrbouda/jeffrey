/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cafe.jeffrey.hub.core.configuration;

import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.appinitializer.ApplicationInitializer;
import cafe.jeffrey.hub.core.appinitializer.DefaultWorkspaceInitializer;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.session.FileHeartbeatReader;
import cafe.jeffrey.hub.core.project.session.SessionFinisher;
import cafe.jeffrey.hub.core.workspace.reconcile.WorkspaceReconciler;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;

/**
 * How the hub keeps its rows in step with the volume: the reconciler that materialises what
 * the provisioner announced, the finisher that closes a session whose heartbeat stopped, and
 * the two initializers that seed a fresh hub at startup.
 */
@Configuration
public class ReconciliationConfiguration {

    @Bean
    public ApplicationInitializer applicationInitializer(HubPlatformRepositories platformRepositories) {
        return new ApplicationInitializer(platformRepositories.newProfilerRepository());
    }

    @Bean
    public DefaultWorkspaceInitializer defaultWorkspaceInitializer(
            WorkspacesManager workspacesManager, DefaultWorkspaceProperties defaultWorkspaceProperties) {
        return new DefaultWorkspaceInitializer(workspacesManager, defaultWorkspaceProperties);
    }

    @Bean
    public SessionFinisher sessionFinisher(Clock clock, HubPlatformRepositories platformRepositories) {
        return new SessionFinisher(clock, new FileHeartbeatReader(), platformRepositories);
    }

    @Bean
    public WorkspaceReconciler workspaceReconciler(
            Clock clock,
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher,
            TransactionOperations hubTransactionOperations) {
        return new WorkspaceReconciler(clock, jeffreyDirs, platformRepositories, sessionFinisher, hubTransactionOperations);
    }
}
