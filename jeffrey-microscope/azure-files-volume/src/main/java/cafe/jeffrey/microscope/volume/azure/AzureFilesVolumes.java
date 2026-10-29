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

package cafe.jeffrey.microscope.volume.azure;

import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import com.azure.core.http.HttpClient;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.file.share.ShareClient;
import com.azure.storage.file.share.ShareClientBuilder;
import com.azure.storage.file.share.models.ShareTokenIntent;

/**
 * Opens the volume an {@link AzureFilesAddress} names, signed in with its credential. The HTTP
 * transport is the JDK's own, found by the SDK on the module path. Every request it sends is
 * logged by {@link AzureFilesTraffic}.
 */
public final class AzureFilesVolumes {

    private AzureFilesVolumes() {
    }

    public static AzureFilesVolume open(AzureFilesAddress address, AzureFilesCredential credential) {
        return open(address, credential, null);
    }

    /**
     * @param httpClient the transport to send requests over; {@code null} for the SDK's own choice
     */
    static AzureFilesVolume open(AzureFilesAddress address, AzureFilesCredential credential, HttpClient httpClient) {
        return open(address, credential, httpClient, new AzureFilesTraffic(address.share()));
    }

    /**
     * @param httpClient the transport to send requests over; {@code null} for the SDK's own choice
     * @param traffic    what sees each request the share answers or fails, retries included
     */
    static AzureFilesVolume open(
            AzureFilesAddress address, AzureFilesCredential credential, HttpClient httpClient, AzureFilesTraffic traffic) {

        ShareClientBuilder builder = new ShareClientBuilder()
                .endpoint(address.endpoint())
                .shareName(address.share())
                .addPolicy(traffic);
        if (httpClient != null) {
            builder.httpClient(httpClient);
        }
        ShareClient share = switch (credential) {
            case AzureFilesCredential.Sas(String token) -> builder.sasToken(token).buildClient();
            // Azure Files grants OAuth over REST only to a request declaring the backup intent
            case AzureFilesCredential.Entra() -> builder
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .shareTokenIntent(ShareTokenIntent.BACKUP)
                    .buildClient();
        };
        return new AzureFilesVolume(share, address.rootPath(), address.protocol());
    }
}
