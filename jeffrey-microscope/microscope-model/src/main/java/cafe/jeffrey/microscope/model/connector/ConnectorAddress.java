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

package cafe.jeffrey.microscope.model.connector;

/**
 * Where the one configured connector's recording volume lies: one kind per block under
 * {@code jeffrey.microscope.connector.*}. Every kind holds the same layout — workspaces directly
 * under its root, then projects, instances, sessions and their files — so everything above the
 * storage reads it the same way.
 */
public sealed interface ConnectorAddress permits AzureFilesAddress, LocalDirectoryAddress {

    /** Which storage it is, and so which configuration block declares it. */
    ConnectorKind kind();

    /**
     * What makes two addresses the same connector — what a download's {@code origin.connectorAddress}
     * tag records, so a copy is recognised as this connector's. Never carries a credential.
     */
    String identity();

    /** Where the volume lies, as the UI shows it. */
    String location();

    /** Whether Microscope refuses every change to the volume. */
    boolean readOnly();
}
