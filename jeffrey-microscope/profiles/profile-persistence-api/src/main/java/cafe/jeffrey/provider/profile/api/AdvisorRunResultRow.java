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

package cafe.jeffrey.provider.profile.api;

import java.time.Instant;

/**
 * The stored timeline of the last Advisor batch run. {@code resultJson} is the serialized run result
 * (per-type, per-step durations) — kept opaque here so the advisor domain owns its shape and the
 * persistence layer just holds the string, exactly as the heap dump keeps its init-pipeline JSON.
 *
 * @param resultJson  the serialized run result
 * @param completedAt when the run finished
 */
public record AdvisorRunResultRow(String resultJson, Instant completedAt) {
}
