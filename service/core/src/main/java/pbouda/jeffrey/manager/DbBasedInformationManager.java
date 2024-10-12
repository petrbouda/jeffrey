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
import pbouda.jeffrey.filesystem.ProfileDirs;
import pbouda.jeffrey.jfr.configuration.ProfileInformationProvider;
import pbouda.jeffrey.repository.CacheKey;
import pbouda.jeffrey.repository.CacheRepository;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.util.Optional;

public class DbBasedInformationManager implements InformationManager {

    private final ProfileInformationProvider infoProvider;
    private final CacheRepository cacheRepository;

    public DbBasedInformationManager(
            ProfileInfo profileInfo,
            ProfileDirs profileDirs,
            CacheRepository cacheRepository) {

        this.cacheRepository = cacheRepository;
        this.infoProvider = new ProfileInformationProvider(profileDirs.allRecordings());
    }

    @Override
    public JsonNode information() {
        Optional<JsonNode> infoOpt = cacheRepository.get(CacheKey.INFO);

        if (infoOpt.isPresent()) {
            return infoOpt.get();
        } else {
            ObjectNode jsonContent = infoProvider.get();
            cacheRepository.insert(CacheKey.INFO, jsonContent);
            return jsonContent;
        }
    }
}
