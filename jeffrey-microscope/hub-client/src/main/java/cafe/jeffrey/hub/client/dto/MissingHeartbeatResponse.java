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

package cafe.jeffrey.hub.client.dto;

import cafe.jeffrey.microscope.model.repository.MissingHeartbeat;

/**
 * Why a session ended without a heartbeat, as the UI reads it — {@code null} on a session that sent one.
 *
 * @param agentPresent whether the Jeffrey Agent jar was found in the session directory
 */
public record MissingHeartbeatResponse(boolean agentPresent) {

    public static MissingHeartbeatResponse from(MissingHeartbeat missing) {
        return missing == null ? null : new MissingHeartbeatResponse(missing.agentPresent());
    }

    public static MissingHeartbeat toModel(MissingHeartbeatResponse response) {
        return response == null ? null : new MissingHeartbeat(response.agentPresent());
    }
}
