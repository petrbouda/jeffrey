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

import tools.jackson.databind.JsonNode;

/**
 * Detail payload for a single session within an instance: session metadata
 * plus the one-shot JFR environment events from the session's latest
 * finished recording chunk.
 *
 * <p>{@code environment} is the raw JSON tree keyed by JFR event type name
 * (e.g. {@code "jdk.JVMInformation"}, {@code "jdk.Shutdown"}) — the same
 * shape produced by {@code EventFieldsToJsonMapper} on the server. Passed
 * through unchanged so new JFR fields appear on the frontend without a
 * schema change. {@code null} when no finished recording chunk has been
 * written yet for this session.
 */
public record InstanceSessionDetailResponse(
        InstanceSessionResponse session,
        JsonNode environment) {
}
