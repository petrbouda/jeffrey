/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.manager.workspace.SandboxWorkspacesManager;
import pbouda.jeffrey.manager.workspace.RemoteWorkspacesManager;
import pbouda.jeffrey.manager.workspace.LocalWorkspacesManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.manager.workspace.remote.RemoteWorkspaceClient;
import pbouda.jeffrey.manager.workspace.remote.RemoteWorkspaceClientImpl;
import pbouda.jeffrey.manager.workspace.local.LocalWorkspaceManager;
import pbouda.jeffrey.manager.workspace.sandbox.SandboxWorkspaceManager;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Clock;

@Configuration
@Import(AppConfiguration.class)
public class WorkspaceConfiguration {

    @Bean
    public CompositeWorkspacesManager compositeWorkspacesManager(
            Repositories repositories,
            SandboxWorkspacesManager sandboxWorkspacesManager,
            LocalWorkspacesManager localWorkspacesManager,
            RemoteWorkspacesManager remoteWorkspacesManager) {

        return new CompositeWorkspacesManager(
                repositories.newWorkspacesRepository(),
                sandboxWorkspacesManager,
                remoteWorkspacesManager,
                localWorkspacesManager);
    }

    @Bean
    public SandboxWorkspacesManager localWorkspacesManager(
            Clock clock,
            Repositories repositories,
            ProjectManager.Factory projectManagerFactory) {

        WorkspaceManager.Factory workspaceManagerFactory = workspaceInfo -> {
            WorkspaceRepository workspaceRepository = repositories.newWorkspaceRepository(workspaceInfo.id());
            return new SandboxWorkspaceManager(workspaceInfo, workspaceRepository, projectManagerFactory);
        };

        return new SandboxWorkspacesManager(clock, repositories.newWorkspacesRepository(), workspaceManagerFactory);
    }

    @Bean
    public LocalWorkspacesManager localWorkspaceManager(
            HomeDirs homeDirs,
            Repositories repositories,
            ProjectManager.Factory projectManagerFactory) {

        WorkspaceManager.Factory workspaceManagerFactory = workspaceInfo -> {
            WorkspaceRepository workspaceRepository = repositories.newWorkspaceRepository(workspaceInfo.id());
            return new LocalWorkspaceManager(homeDirs, workspaceInfo, workspaceRepository, projectManagerFactory);
        };

        return new LocalWorkspacesManager(repositories.newWorkspacesRepository(), workspaceManagerFactory);
    }

    @Bean
    public RemoteWorkspacesManager mirroringWorkspacesManager(
            Repositories repositories,
            RemoteWorkspaceClient.Factory mirrorWorkspaceClientFactory) {

        return new RemoteWorkspacesManager(repositories.newWorkspacesRepository(), mirrorWorkspaceClientFactory);
    }

    @Bean
    public RemoteWorkspaceClient.Factory mirrorWorkspaceClientFactory() {
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

            RestClient.Builder clientBuilder = RestClient.builder()
                    .requestFactory(new JdkClientHttpRequestFactory(httpClient));

            return new RemoteWorkspaceClientImpl(remoteUrl, clientBuilder);
        };
    }
}
