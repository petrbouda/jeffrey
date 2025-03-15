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

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.provider.api.model.graph.GraphMetadata;
import pbouda.jeffrey.provider.api.model.graph.SavedGraphData;
import pbouda.jeffrey.provider.api.repository.ProfileGraphRepository;

import java.util.List;
import java.util.Optional;

public class GraphRepositoryManagerImpl implements GraphRepositoryManager {

    private final FlamegraphManager flamegraphManager;
    private final ProfileGraphRepository repository;

    public GraphRepositoryManagerImpl(FlamegraphManager flamegraphManager, ProfileGraphRepository repository) {
        this.flamegraphManager = flamegraphManager;
        this.repository = repository;
    }

    @Override
    public void save(GraphParameters graphParameters, String flamegraphName) {
        GraphData generated = flamegraphManager.generate(graphParameters);

        JsonNode graphData = Json.toTree(generated);
        JsonNode graphParams = Json.toTree(graphParameters);
        GraphMetadata graphMetadata = new GraphMetadata(flamegraphName, graphParams);

        repository.insert(graphMetadata, graphData);
    }

    @Override
    public Optional<GraphContentWithMetadata> get(String graphId) {
        return repository.get(graphId)
                .map(GraphRepositoryManagerImpl::toGraphContent);
    }

    @Override
    public List<GraphMetadataWithGenerateRequest> list() {
        return repository.getAllMetadata().stream()
                .map(GraphRepositoryManagerImpl::toGraphMetadata)
                .toList();
    }

    @Override
    public void delete(String graphId) {
        repository.delete(graphId);
    }

    private static GraphContentWithMetadata toGraphContent(SavedGraphData savedGraphData) {
        return new GraphContentWithMetadata(
                toGraphMetadata(savedGraphData.metadata()),
                Json.treeToValue(savedGraphData.content(), GraphData.class));
    }

    private static GraphMetadataWithGenerateRequest toGraphMetadata(GraphMetadata metadata) {
        return new GraphMetadataWithGenerateRequest(
                metadata.id(),
                metadata.name(),
                Json.treeToValue(metadata.params(), GraphParameters.class),
                metadata.createdAt());
    }
}
