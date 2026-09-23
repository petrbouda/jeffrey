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
package cafe.jeffrey.hub.core.configuration;

import cafe.jeffrey.hub.core.HubJeffreyDirs;
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
 * the initializer that seeds a fresh hub with its default workspace at startup.
 */
@Configuration
public class ReconciliationConfiguration {

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
