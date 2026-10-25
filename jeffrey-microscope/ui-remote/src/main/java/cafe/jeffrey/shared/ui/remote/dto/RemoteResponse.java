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

package cafe.jeffrey.shared.ui.remote.dto;

/**
 * The connected remote.
 *
 * @param hostname  a Jeffrey Hub's host; {@code null} for an Azure file share
 * @param port      a Jeffrey Hub's port; {@code 0} for an Azure file share
 * @param plaintext whether a Jeffrey Hub is reached in cleartext; {@code false} for a file share
 * @param source    {@code "CONFIG"} when configuration declares it — the UI cannot disconnect it,
 *                  because the next startup would connect it again — or {@code "USER"} when the UI
 *                  connected it. Carried as the enum name so the DTO stays a plain record over
 *                  JSON-friendly types.
 * @param kind      {@code "GRPC"} for a Jeffrey Hub, {@code "AZURE_FILES"} for the recording volume
 *                  on an Azure file share
 * @param location  where it lies, as a person reads it: {@code host:port}, or the share's endpoint,
 *                  name and directory
 * @param readOnly  whether it refuses every change: no session deletes or pins, and no workspace
 *                  created or removed through it
 */
public record RemoteResponse(
        String name,
        String hostname,
        int port,
        boolean plaintext,
        long createdAt,
        String source,
        String kind,
        String location,
        boolean readOnly) {
}
