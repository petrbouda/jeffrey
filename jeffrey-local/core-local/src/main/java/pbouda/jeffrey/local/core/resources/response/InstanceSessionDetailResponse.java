/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package pbouda.jeffrey.local.core.resources.response;

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
