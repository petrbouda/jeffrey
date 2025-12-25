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

package pbouda.jeffrey.platform.configuration.workspace;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.platform.configuration.AppConfiguration;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.manager.workspace.RemoteWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteWorkspaceClient;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteWorkspaceClientImpl;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteWorkspaceManager;
import pbouda.jeffrey.provider.api.repository.Repositories;

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
            Repositories repositories,
            RemoteWorkspaceClient.Factory remoteWorkspaceClientFactory,
            @Qualifier(WorkspaceConfiguration.COMMON_PROJECTS_TYPE)
            ProjectsManager.Factory commonProjectsManagerFactory) {

        WorkspaceManager.Factory workspaceManagerFactory = workspaceInfo -> {
            URI baseUri = workspaceInfo.baseLocation().toUri();
            return new RemoteWorkspaceManager(
                    jeffreyDirs,
                    workspaceInfo,
                    repositories.newWorkspaceRepository(workspaceInfo.id()),
                    remoteWorkspaceClientFactory.apply(baseUri),
                    commonProjectsManagerFactory);
        };

        return new RemoteWorkspacesManager(
                repositories.newWorkspacesRepository(),
                workspaceManagerFactory,
                remoteWorkspaceClientFactory);
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
