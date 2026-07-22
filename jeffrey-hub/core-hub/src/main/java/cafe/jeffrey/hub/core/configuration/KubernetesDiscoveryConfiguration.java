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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.kubernetes.KubernetesDiscovery;
import cafe.jeffrey.hub.core.kubernetes.PodLifecycleHandler;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.streaming.SessionFinisher;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;

import java.time.Clock;

/**
 * Optional Kubernetes-native discovery, activated with
 * {@code jeffrey.hub.kubernetes.enabled=true}. The client connects with the
 * standard in-cluster configuration (service account) or the local kubeconfig
 * when running outside a cluster.
 */
@Configuration
@ConditionalOnProperty(name = "jeffrey.hub.kubernetes.enabled", havingValue = "true")
public class KubernetesDiscoveryConfiguration {

    @Bean
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }

    @Bean
    public PodLifecycleHandler podLifecycleHandler(
            WorkspacesManager workspacesManager,
            HubPlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher,
            HubJeffreyDirs jeffreyDirs,
            Clock clock,
            @Value("${jeffrey.hub.kubernetes.auto-create-workspaces:true}") boolean autoCreateWorkspaces) {

        return new PodLifecycleHandler(
                workspacesManager, platformRepositories, sessionFinisher, jeffreyDirs, clock, autoCreateWorkspaces);
    }

    @Bean(destroyMethod = "close")
    public KubernetesDiscovery kubernetesDiscovery(
            KubernetesClient kubernetesClient,
            PodLifecycleHandler podLifecycleHandler,
            @Value("${jeffrey.hub.kubernetes.namespace:}") String namespace) {

        return new KubernetesDiscovery(kubernetesClient, podLifecycleHandler, namespace);
    }
}
