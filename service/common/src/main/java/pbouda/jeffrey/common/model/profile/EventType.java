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

package pbouda.jeffrey.common.model.profile;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.EventSource;

import java.util.List;
import java.util.Map;

public record EventType(
        String name,
        String label,
        Long typeId,
        String description,
        List<String> categories,
        EventSource source,
        String subtype,
        long samples,
        Long weight,
        boolean hasStacktrace,
        boolean calculated,
        Map<String, String> extras,
        JsonNode columns) {
}
