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

import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.provider.api.model.graph.GraphContent;
import pbouda.jeffrey.provider.api.model.graph.GraphInfo;
import pbouda.jeffrey.provider.api.repository.ProfileGraphRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class AbstractFlamegraphManager implements FlamegraphManager {

    private final ProfileInfo profileInfo;
    private final ProfileGraphRepository repository;

    public AbstractFlamegraphManager(
            ProfileInfo profileInfo,
            ProfileGraphRepository repository) {

        this.profileInfo = profileInfo;
        this.repository = repository;
    }

    @Override
    public List<GraphInfo> allCustom() {
        return repository.allCustom(profileInfo.id());
    }

    @Override
    public Optional<GraphContent> get(String flamegraphId) {
        return repository.content(profileInfo.id(), flamegraphId);
    }

    @Override
    public void delete(String flamegraphId) {
        repository.delete(profileInfo.id(), flamegraphId);
    }

    protected void generateAndSave(GraphInfo graphInfo, Supplier<GraphData> generator) {
        GraphData generated = generator.get();
        repository.insert(graphInfo, Json.toTree(generated));
    }
}
