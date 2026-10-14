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

package cafe.jeffrey.hub.core.volume;

/**
 * The hub's own marker in a workspace directory: what a workspace is called and when it was
 * created. The workspace's id is its directory name, which is also the reference id the
 * provisioner writes into.
 *
 * @param createdAt epoch millis
 */
public record WorkspaceMarker(String name, long createdAt) {

    public static final String FILE_NAME = ".workspace-info.json";

    public WorkspaceMarker {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A workspace marker needs a name");
        }
    }
}
