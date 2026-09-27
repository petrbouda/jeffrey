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

package cafe.jeffrey.microscope.mcp.protocol;

import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;

/**
 * One task as its provider reports it: the id the client follows it by, where it stands, and the
 * timing fields of a {@code CreateTaskResult}.
 *
 * @param taskId        the id the client passes to {@code tasks/get}, {@code tasks/update} and {@code tasks/cancel}
 * @param state         where the task stands
 * @param createdAt     when the work started
 * @param lastUpdatedAt when the task last changed; never before {@code createdAt}
 * @param ttl           how long the task stays readable after it finishes — retention, not a cache hint
 * @param pollInterval  how often a client should ask again, or null to leave it to the client
 */
public record McpTask(
        String taskId,
        McpTaskState state,
        Instant createdAt,
        Instant lastUpdatedAt,
        Duration ttl,
        Duration pollInterval) {

    private static final String FIELD_TASK_ID = "taskId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_STATUS_MESSAGE = "statusMessage";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_UPDATED_AT = "lastUpdatedAt";
    private static final String FIELD_TTL_MS = "ttlMs";
    private static final String FIELD_POLL_INTERVAL_MS = "pollIntervalMs";

    public McpTask {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("A task needs an id");
        }
        requirePresent(state, "state");
        requirePresent(createdAt, "createdAt");
        requirePresent(lastUpdatedAt, "lastUpdatedAt");
        requirePresent(ttl, "ttl");
        if (lastUpdatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("A task cannot change before it was created: createdAt="
                    + createdAt + " lastUpdatedAt=" + lastUpdatedAt);
        }
        requireNotNegative(ttl, FIELD_TTL_MS);
        if (pollInterval != null) {
            requireNotNegative(pollInterval, FIELD_POLL_INTERVAL_MS);
        }
    }

    /** The status the task reports. */
    public McpTaskStatus status() {
        return state.status();
    }

    /**
     * The task's own fields — {@code taskId}, {@code status}, {@code statusMessage} when there is one,
     * {@code createdAt} and {@code lastUpdatedAt} in ISO-8601, {@code ttlMs} and {@code pollIntervalMs}
     * when set. What a {@code CreateTaskResult} holds, and what {@code tasks/get} starts from; the
     * envelope adds {@code resultType}, the server's identity and, from {@code tasks/get}, the result.
     */
    public ObjectNode toJson() {
        ObjectNode json = McpJson.createObject();
        json.put(FIELD_TASK_ID, taskId);
        json.put(FIELD_STATUS, state.status().wireName());
        String statusMessage = state.statusMessage();
        if (statusMessage != null) {
            json.put(FIELD_STATUS_MESSAGE, statusMessage);
        }
        json.put(FIELD_CREATED_AT, createdAt.toString());
        json.put(FIELD_LAST_UPDATED_AT, lastUpdatedAt.toString());
        json.put(FIELD_TTL_MS, ttl.toMillis());
        if (pollInterval != null) {
            json.put(FIELD_POLL_INTERVAL_MS, pollInterval.toMillis());
        }
        return json;
    }

    private static void requirePresent(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("A task needs its " + name);
        }
    }

    private static void requireNotNegative(Duration duration, String name) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative: " + duration);
        }
    }
}
