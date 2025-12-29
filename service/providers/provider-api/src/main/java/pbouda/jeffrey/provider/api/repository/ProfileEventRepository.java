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

package pbouda.jeffrey.provider.api.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.provider.api.repository.model.AllocatingThread;

import java.util.List;
import java.util.Optional;

public interface ProfileEventRepository {

    Optional<ObjectNode> latestJsonFields(Type type);

    List<AllocatingThread> allocatingThreads(int limit);

    List<JsonNode> eventsByTypeWithFields(Type type);

    boolean containsEventType(Type type);
}
