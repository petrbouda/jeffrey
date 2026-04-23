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

package pbouda.jeffrey.profile.guardian.metadata;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.provider.profile.model.AllocatingThread;
import pbouda.jeffrey.provider.profile.model.EventDurationStats;
import pbouda.jeffrey.provider.profile.model.JvmFlag;
import pbouda.jeffrey.provider.profile.model.JvmFlagDetail;
import pbouda.jeffrey.provider.profile.repository.ProfileEventRepository;
import pbouda.jeffrey.shared.common.model.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minimal test stub — only {@link #durationStatsByType(Type)} is exercised. */
final class StubEventRepository implements ProfileEventRepository {

    private final Map<String, EventDurationStats> stats = new HashMap<>();

    StubEventRepository put(Type type, EventDurationStats s) {
        stats.put(type.code(), s);
        return this;
    }

    @Override
    public EventDurationStats durationStatsByType(Type type) {
        return stats.getOrDefault(type.code(), EventDurationStats.EMPTY);
    }

    // ===== unused by the metadata evaluator tests =====

    @Override public Optional<ObjectNode> latestJsonFields(Type type) { return Optional.empty(); }
    @Override public List<AllocatingThread> allocatingThreads(int limit) { return List.of(); }
    @Override public List<JsonNode> eventsByTypeWithFields(Type type) { return List.of(); }
    @Override public boolean containsEventType(Type type) { return false; }
    @Override public List<JvmFlag> getStringRelatedFlags() { return List.of(); }
    @Override public List<JvmFlagDetail> getAllFlags() { return List.of(); }
}
