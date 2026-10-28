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

/**
 * What Microscope signs in to an Azure file share with. Held in memory only, read from
 * configuration; nothing stores the secret.
 */
public sealed interface AzureFilesCredential {

    /**
     * A shared access signature scoped to the share: read and list, plus delete for a share that
     * takes the user's changes.
     *
     * @param token the SAS query string, with or without its leading {@code ?}
     */
    record Sas(String token) implements AzureFilesCredential {

        public Sas {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("A SAS credential needs its token");
            }
        }

        /** Never the token itself, which would otherwise end up in a log line. */
        @Override
        public String toString() {
            return "Sas[token=<redacted>]";
        }
    }

    /**
     * The environment's Microsoft Entra identity, found the way {@code DefaultAzureCredential}
     * looks: environment variables, workload identity, managed identity, the Azure CLI login.
     * Requests carry the backup intent Azure Files requires of OAuth over REST.
     */
    record Entra() implements AzureFilesCredential {
    }
}
