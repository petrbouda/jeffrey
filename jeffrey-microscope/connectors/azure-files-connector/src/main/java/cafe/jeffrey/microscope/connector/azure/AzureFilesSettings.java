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

package cafe.jeffrey.microscope.connector.azure;

/**
 * The {@code azure-files} block of the connector declaration
 * ({@code jeffrey.microscope.connector.azure-files.*}): the share holding the recording volume, and how
 * to sign in to it. A setting left out is {@code null}; the protocol and the sign-in then take their
 * defaults, {@code smb} and {@code sas}.
 *
 * @param endpoint the storage account's file endpoint, {@code https://<account>.file.core.windows.net}
 * @param share    the file share's name
 * @param rootPath the directory within the share that holds the workspaces; the share's root when left out
 * @param protocol {@code smb} or {@code nfs}
 * @param auth     {@code sas} or {@code entra}
 * @param sasToken the share-scoped SAS token, required when {@code auth=sas}; never printed
 * @param readOnly whether Microscope refuses every change to the volume
 */
public record AzureFilesSettings(
        String endpoint,
        String share,
        String rootPath,
        String protocol,
        String auth,
        String sasToken,
        boolean readOnly) {

    private static final String DEFAULT_PROTOCOL = "smb";
    private static final String DEFAULT_AUTH = "sas";
    private static final String HIDDEN = "<hidden>";

    public AzureFilesSettings {
        protocol = protocol == null ? DEFAULT_PROTOCOL : protocol;
        auth = auth == null ? DEFAULT_AUTH : auth;
    }

    /** Every setting but the SAS token, which is only said to be there. */
    @Override
    public String toString() {
        return "AzureFilesSettings[endpoint=" + endpoint + ", share=" + share + ", rootPath=" + rootPath
                + ", protocol=" + protocol + ", auth=" + auth + ", sasToken=" + (sasToken == null ? null : HIDDEN)
                + ", readOnly=" + readOnly + "]";
    }
}
