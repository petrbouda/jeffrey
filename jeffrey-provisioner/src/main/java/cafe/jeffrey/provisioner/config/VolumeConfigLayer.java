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


package cafe.jeffrey.provisioner.config;

import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.model.repository.AppliedConfigLayer;
import com.typesafe.config.Config;

import java.nio.file.Path;

/**
 * One hub-published file found on the shared volume, ready to take its place in the merge.
 *
 * @param scope  which folder it came from, which is also its rank in the merge
 * @param file   where it was read from, for logging
 * @param digest SHA-256 of the bytes as they were read, recorded in the session marker so the file
 *               a session ran with can be matched against what the hub holds now
 * @param config the parsed content, already stripped of any key a published file may not set
 */
public record VolumeConfigLayer(ConfigScope scope, Path file, String digest, Config config) {

    public VolumeConfigLayer {
        if (scope == null || file == null || config == null) {
            throw new IllegalArgumentException("scope, file and config must not be null");
        }
        if (digest == null || digest.isBlank()) {
            throw new IllegalArgumentException("digest must not be blank");
        }
    }

    public AppliedConfigLayer applied() {
        return new AppliedConfigLayer(scope, digest);
    }
}
