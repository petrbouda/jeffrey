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

import cafe.jeffrey.microscope.model.repository.SessionHeartbeat;
import cafe.jeffrey.shared.common.InstantUtils;

/**
 * A session's liveness as the UI reads it.
 *
 * @param missing         no heartbeat arrived within the startup grace — the session is not live
 * @param lastHeartbeatAt the last heartbeat of a session still open (epoch millis), or {@code null}
 * @param agentPresent    whether the Jeffrey Agent jar was found in the session directory
 */
public record SessionHeartbeatResponse(boolean missing, Long lastHeartbeatAt, boolean agentPresent) {

    public static final SessionHeartbeatResponse UNKNOWN = new SessionHeartbeatResponse(false, null, false);

    public static SessionHeartbeatResponse from(SessionHeartbeat heartbeat) {
        return new SessionHeartbeatResponse(
                heartbeat.missing(), InstantUtils.toEpochMilli(heartbeat.lastHeartbeatAt()), heartbeat.agentPresent());
    }

    public SessionHeartbeat toModel() {
        return new SessionHeartbeat(missing, InstantUtils.fromEpochMilli(lastHeartbeatAt), agentPresent);
    }
}
