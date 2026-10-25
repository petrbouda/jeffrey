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

package cafe.jeffrey.microscope.model.hub;

import java.util.Locale;
import java.util.Optional;

/**
 * The protocol an Azure file share was created for, which decides what its REST listing carries:
 * an SMB share lists each entry with its timestamps, an NFS share with its size alone.
 */
public enum AzureFilesProtocol {

    /** What the AKS {@code azurefile-csi} storage classes create. */
    SMB,

    /** An SSD share mounted over NFS 4.1; listed over REST from API version 2025-05-05. */
    NFS;

    /** The value as it is spelled in configuration and stored, or empty for anything else. */
    public static Optional<AzureFilesProtocol> parse(String value) {
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
