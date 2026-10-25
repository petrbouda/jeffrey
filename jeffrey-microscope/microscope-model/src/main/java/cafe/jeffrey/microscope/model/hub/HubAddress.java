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

/**
 * Where a source of recordings lies: a jeffrey-hub reached over gRPC, or the shared volume itself,
 * read straight from cloud storage with no hub in between. Every kind lists the same tree —
 * workspaces, projects, instances, sessions, files — so everything above the clients that read it
 * treats them alike.
 */
public sealed interface HubAddress permits GrpcHubAddress, AzureFilesAddress {

    /**
     * What makes two addresses the same source, and what the registry keeps unique: never a
     * credential and never a display preference.
     */
    String identity();

    /**
     * Whether this source can only be read. A volume read straight from storage is shared with
     * the hub that maintains it, and Microscope never changes it: no deletes, pins or workspaces.
     */
    boolean readOnly();
}
