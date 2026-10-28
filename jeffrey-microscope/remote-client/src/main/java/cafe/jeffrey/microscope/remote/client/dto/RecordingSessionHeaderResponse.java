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

import cafe.jeffrey.microscope.model.repository.RecordingSessionHeader;
import cafe.jeffrey.shared.common.InstantUtils;

/**
 * A session as its instance's listing names it, on the wire: nothing of it read.
 *
 * @param createdAt epoch millis, off its name
 */
public record RecordingSessionHeaderResponse(String id, String instanceId, long createdAt, boolean newest) {

    public static RecordingSessionHeaderResponse from(RecordingSessionHeader session) {
        return new RecordingSessionHeaderResponse(
                session.id(), session.instanceId(), session.createdAt().toEpochMilli(), session.newest());
    }

    public RecordingSessionHeader toHeader() {
        return new RecordingSessionHeader(id, instanceId, InstantUtils.fromEpochMilli(createdAt), newest);
    }
}
