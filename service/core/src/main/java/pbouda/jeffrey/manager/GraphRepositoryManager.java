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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.flamegraph.api.GraphData;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface GraphRepositoryManager {

    record GraphMetadataWithGenerateRequest(
            String id,
            String name,
            GraphParameters graphParameters,
            Instant createdAt) {
    }

    record GraphContentWithMetadata(GraphMetadataWithGenerateRequest metadata, GraphData content) {
    }

    @FunctionalInterface
    interface Factory extends Function<FlamegraphManager, GraphRepositoryManager> {
    }

    void save(GraphParameters parameters, String flamegraphName);

    Optional<GraphContentWithMetadata> get(String graphId);

    List<GraphMetadataWithGenerateRequest> list();

    void delete(String graphId);
}
