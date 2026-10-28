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

package cafe.jeffrey.microscope.remote.client.dto;

import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo;

/**
 * An instance as this application's API reports it: its directory.
 *
 * @param createdAt epoch millis when its directory was created; {@code null} on a share that lists no times
 */
public record InstanceResponse(String id, String instanceName, Long createdAt) {

    /** An instance as a remote lists it: named by its id. */
    public static InstanceResponse summary(String id, Long createdAt) {
        return new InstanceResponse(id, id, createdAt);
    }

    public static InstanceResponse from(ProjectInstanceInfo info) {
        return new InstanceResponse(info.id(), info.instanceName(), InstantUtils.toEpochMilli(info.startedAt()));
    }
}
