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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.workspace.LocalWorkspaceManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManagerImpl;
import pbouda.jeffrey.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.manager.workspace.WorkspacesManagerImpl;
import pbouda.jeffrey.manager.workspace.mirror.MirroringWorkspaceClient;
import pbouda.jeffrey.manager.workspace.mirror.MirroringWorkspaceClientImpl;
import pbouda.jeffrey.manager.workspace.mirror.MirroringWorkspaceManager;
import pbouda.jeffrey.manager.workspace.mirror.MirroringWorkspacesManager;
import pbouda.jeffrey.manager.workspace.mirror.NoOpMirroringWorkspaceClient;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Configuration
public class WorkspaceConfiguration {

    @Bean
    public WorkspacesManager workspaceManager(
            HomeDirs homeDirs,
            Repositories repositories,
            ProjectManager.Factory projectManagerFactory,
            MirroringWorkspaceClient.Factory mirrorWorkspaceClientFactory) {
        WorkspaceManager.Factory workspaceManagerFactory = workspaceInfo -> {
            WorkspaceRepository workspaceRepository = repositories.newWorkspaceRepository(workspaceInfo.id());
            if (LocalWorkspaceManager.LOCAL_WORKSPACE_ID.equalsIgnoreCase(workspaceInfo.id())) {
                return new LocalWorkspaceManager(
                        workspaceInfo, repositories.newProjectsRepository(), projectManagerFactory);
            }

            if (workspaceInfo.isMirrored()) {
                URI baseLocation = workspaceInfo.baseLocation().toUri();
                MirroringWorkspaceClient client = mirrorWorkspaceClientFactory.apply(baseLocation);
                return new MirroringWorkspaceManager(workspaceInfo, client);
            } else {
                return new WorkspaceManagerImpl(homeDirs, workspaceInfo, workspaceRepository, projectManagerFactory);
            }
        };

        return new WorkspacesManagerImpl(
                repositories.newProjectsRepository(), repositories.newWorkspacesRepository(), workspaceManagerFactory);
    }

    @Bean
    public WorkspaceManager.Factory workspaceManagerFactory(
            HomeDirs homeDirs, Repositories repositories, ProjectManager.Factory projectManagerFactory) {
        return workspaceInfo -> {
            WorkspaceRepository workspaceRepository = repositories.newWorkspaceRepository(workspaceInfo.id());
            return new WorkspaceManagerImpl(homeDirs, workspaceInfo, workspaceRepository, projectManagerFactory);
        };
    }

    @Bean
    public MirroringWorkspaceClient.Factory mirrorWorkspaceClientFactory() {
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

            return new MirroringWorkspaceClientImpl(remoteUrl, clientBuilder);
        };
    }

    @Bean
    public MirroringWorkspacesManager.Factory mirroringWorkspacesManagerFactory(
            WorkspacesManager workspacesManager,
            MirroringWorkspaceClient.Factory mirrorWorkspaceClientFactory) {

        return uri -> {
            MirroringWorkspaceClient client = mirrorWorkspaceClientFactory.apply(uri);
            return new MirroringWorkspacesManager(workspacesManager, client);
        };
    }
}
