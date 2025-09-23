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
import pbouda.jeffrey.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.manager.workspace.mirror.MirroringWorkspacesManager;
import pbouda.jeffrey.manager.workspace.mirror.MirroringWorkspacesManagerImpl;
import pbouda.jeffrey.manager.workspace.mirror.MirroringWorkspaceClient;
import pbouda.jeffrey.manager.workspace.mirror.MirroringWorkspaceClientImpl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Configuration
public class WorkspaceConfiguration {

    @Bean
    public MirroringWorkspaceClient.Factory mirrorWorkspaceClientFactory() {
        return remoteUrl -> {
            // Create trust-all TrustManager
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
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
            return new MirroringWorkspacesManagerImpl(workspacesManager, client);
        };
    }
}
