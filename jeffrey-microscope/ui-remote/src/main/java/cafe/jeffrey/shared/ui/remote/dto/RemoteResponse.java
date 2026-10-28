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
 * The configured remote, the recording volume on an Azure file share.
 *
 * @param location  where it lies, as a person reads it: the share's endpoint, name and directory
 * @param readOnly  whether it refuses every change: no workspace, project or session deleted
 *                  through it
 */
public record RemoteResponse(
        String name,
        String location,
        boolean readOnly) {
}
