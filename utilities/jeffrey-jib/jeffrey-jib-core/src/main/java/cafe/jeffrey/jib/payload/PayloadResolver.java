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

package cafe.jeffrey.jib.payload;

import java.nio.file.Path;

/**
 * Fetches a payload artifact through the running build system's own dependency resolution.
 *
 * <p>The seam exists because the two build systems share nothing here: Maven resolves through
 * Aether, Gradle through a detached configuration. Keeping the interface in the core module lets
 * the build-plan transformation stay build-system agnostic, the same way
 * {@code JeffreyBuildPlanExtender} already is.
 */
@FunctionalInterface
public interface PayloadResolver {

    /**
     * @return the local file the build system resolved the artifact to
     * @throws PayloadResolutionException the artifact could not be resolved; the build must fail
     */
    Path resolve(ArtifactCoordinates coordinates) throws PayloadResolutionException;
}
