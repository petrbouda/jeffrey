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

package cafe.jeffrey.microscope.model.remote;

import java.util.Locale;
import java.util.Optional;

/**
 * How Microscope signs in to an Azure file share. Neither keeps a secret outside configuration: a
 * SAS token is read from it when the share is opened, and Entra ID takes whatever identity the
 * environment provides.
 */
public enum AzureFilesAuth {

    /**
     * A share-scoped shared access signature: read and list for a read-only share; create, write and
     * delete as well for one that takes the user's changes.
     */
    SAS,

    /**
     * The environment's Microsoft Entra identity (Azure CLI login, workload or managed identity).
     * Azure Files grants REST access to it only through the Storage File Data Privileged Reader role
     * (Contributor for a writable share), which reaches every share of the storage account.
     */
    ENTRA;

    /** The value as it is spelled in configuration, or empty for anything else. */
    public static Optional<AzureFilesAuth> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
