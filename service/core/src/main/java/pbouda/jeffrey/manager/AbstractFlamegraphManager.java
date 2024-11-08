/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.exception.InvalidUserInputException;
import pbouda.jeffrey.generator.flamegraph.GraphExporter;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.common.model.ProfileInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class AbstractFlamegraphManager implements FlamegraphManager {

    private final ProfileInfo profileInfo;
    private final GraphRepository repository;
    private final GraphExporter graphExporter;

    public AbstractFlamegraphManager(
            ProfileInfo profileInfo,
            GraphRepository repository,
            GraphExporter graphExporter) {

        this.profileInfo = profileInfo;
        this.repository = repository;
        this.graphExporter = graphExporter;
    }

    @Override
    public List<GraphInfo> allCustom() {
        return repository.allCustom(profileInfo.id());
    }

    @Override
    public void export(String flamegraphId) {
        GraphContent content = repository.content(profileInfo.id(), flamegraphId)
                .orElseThrow(() -> new InvalidUserInputException("Cannot find a flamegraph to be exported:" + flamegraphId));

        _export(content.content(), Path.of(content.name() + ".html"));
    }

    @Override
    public void export(Type eventType, TimeRangeRequest timeRange, boolean threadMode) {
        Generate generateRequest = new Generate(eventType, timeRange, threadMode, false, false);
        ObjectNode content = generate(generateRequest);
        _export(content, Path.of(generateFilename(eventType) + ".html"));
    }

    protected void _export(JsonNode jsonObject, Path filename) {
        throw new UnsupportedOperationException("Not implemented yet");
//        Path target = workingDirs.exportsDir(profileInfo).resolve(filename);
//        graphExporter.export(target, jsonObject);
    }

    @Override
    public Optional<GraphContent> get(String flamegraphId) {
        return repository.content(profileInfo.id(), flamegraphId);
    }

    @Override
    public void delete(String flamegraphId) {
        repository.delete(profileInfo.id(), flamegraphId);
    }

    protected void generateAndSave(GraphInfo graphInfo, Supplier<ObjectNode> generator) {
        ObjectNode generated = generator.get();
        repository.insert(graphInfo, generated);
    }
}
