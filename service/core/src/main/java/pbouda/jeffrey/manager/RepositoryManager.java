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

import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.common.model.RepositoryType;

import java.nio.file.Path;
import java.util.Optional;

public interface RepositoryManager {

    void createOrReplace(Path repositoryPath, RepositoryType repositoryType, boolean createIfNotExists);

    Optional<RepositoryInfo> info();

    void delete();

    void generate();
}
