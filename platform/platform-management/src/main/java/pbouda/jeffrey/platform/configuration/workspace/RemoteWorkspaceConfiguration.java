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

package pbouda.jeffrey.platform.configuration.workspace;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.platform.configuration.AppConfiguration;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.manager.workspace.RemoteWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteClients;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteDiscoveryClientImpl;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteHttpInvoker;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteInstancesClientImpl;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteMessagesClientImpl;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteProfilerClientImpl;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteRecordingStreamClientImpl;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteRepositoryClientImpl;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteWorkspaceManager;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Configuration
@Import(AppConfiguration.class)
public class RemoteWorkspaceConfiguration {

    @Bean
    public RemoteWorkspacesManager remoteWorkspacesManager(
            JeffreyDirs jeffreyDirs,
            PlatformRepositories platformRepositories,
            RemoteClients.Factory remoteClientsFactory,
            @Qualifier(WorkspaceConfiguration.COMMON_PROJECTS_TYPE)
            ProjectsManager.Factory commonProjectsManagerFactory,
            JobDescriptorFactory jobDescriptorFactory) {

        WorkspaceManager.Factory workspaceManagerFactory = workspaceInfo -> {
            URI baseUri = workspaceInfo.baseLocation().toUri();
            return new RemoteWorkspaceManager(
                    jeffreyDirs,
                    workspaceInfo,
                    platformRepositories.newWorkspaceRepository(workspaceInfo.id()),
                    remoteClientsFactory.apply(baseUri),
                    commonProjectsManagerFactory,
                    platformRepositories,
                    jobDescriptorFactory);
        };

        return new RemoteWorkspacesManager(
                platformRepositories.newWorkspacesRepository(),
                workspaceManagerFactory,
                remoteClientsFactory);
    }

    @Bean
    public RemoteClients.Factory remoteClientsFactory() {
        return remoteUrl -> {
            // Create trust-all TrustManager
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new SecureRandom());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            HttpClient httpClient = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();

            RestClient restClient = RestClient.builder()
                    .baseUrl(remoteUrl)
                    .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                    .build();

            RemoteHttpInvoker invoker = new RemoteHttpInvoker(remoteUrl, restClient);

            return new RemoteClients(
                    new RemoteDiscoveryClientImpl(invoker),
                    new RemoteRepositoryClientImpl(invoker),
                    new RemoteRecordingStreamClientImpl(invoker),
                    new RemoteProfilerClientImpl(invoker),
                    new RemoteMessagesClientImpl(invoker),
                    new RemoteInstancesClientImpl(invoker));
        };
    }
}
