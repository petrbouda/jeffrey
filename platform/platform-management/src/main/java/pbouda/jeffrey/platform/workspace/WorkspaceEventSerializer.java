/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.platform.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.platform.queue.EventSerializer;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.time.Instant;

/**
 * Serializes and deserializes {@link WorkspaceEvent} to/from JSON for persistent queue storage.
 * The {@code eventId} field is excluded from serialization since the queue manages offsets independently.
 */
public class WorkspaceEventSerializer implements EventSerializer<WorkspaceEvent> {

    @Override
    public String serialize(WorkspaceEvent event) {
        return Json.toString(event);
    }

    @Override
    public WorkspaceEvent deserialize(String payload) {
        JsonNode node = Json.readTree(payload);
        return new WorkspaceEvent(
                node.has("eventId") && !node.get("eventId").isNull() ? node.get("eventId").asLong() : null,
                node.get("originEventId").asText(),
                node.get("projectId").asText(),
                node.get("workspaceId").asText(),
                WorkspaceEventType.valueOf(node.get("eventType").asText()),
                node.get("content").asText(),
                Instant.parse(node.get("originCreatedAt").asText()),
                node.has("createdAt") && !node.get("createdAt").isNull()
                        ? Instant.parse(node.get("createdAt").asText()) : null,
                node.get("createdBy").asText()
        );
    }
}
