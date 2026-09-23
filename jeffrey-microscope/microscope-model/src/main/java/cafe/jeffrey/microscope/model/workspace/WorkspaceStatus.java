/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.model.workspace;

public enum WorkspaceStatus {
    // The workspace is available and operational.
    AVAILABLE,
    // The workspace is temporarily unavailable, possibly the workspace has been removed (remote or on live filesystem).
    UNAVAILABLE,
    // The workspace is not reachable at the moment, possibly due to network issues.
    OFFLINE,
    // The status of the workspace has not been already determined.
    UNKNOWN
}
