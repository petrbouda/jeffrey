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

import com.typesafe.config.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The configuration layers that belong to the container, kept apart rather than pre-merged.
 *
 * <p>They have to stay separate because the hub-published layers sit <em>between</em> them: the
 * image's base config is the weakest thing anyone writes, the published files override it, and the
 * deployment's own override file and environment override those in turn. That order is what keeps
 * a pod working when the volume carries something wrong — whoever deploys the container always has
 * the last word.</p>
 *
 * <p>The published layers are not known when these are read: they live in folders named after the
 * workspace and project, which the container layers are what decide. So the merge happens twice,
 * once to learn where this run belongs and once with the files found there.</p>
 */
public record ContainerLayers(Config environment, Config overrideFile, Config baseFile, Config defaults) {

    public ContainerLayers {
        if (environment == null || overrideFile == null || baseFile == null || defaults == null) {
            throw new IllegalArgumentException("every layer must be present, empty rather than null");
        }
    }

    /**
     * Stacks every layer into one configuration, strongest first. Precedence is expressed by this
     * order and nowhere else, so every setting obeys the same rule and none needs an exception.
     */
    public Config resolve(List<VolumeConfigLayer> volumeLayers) {
        Config merged = environment.withFallback(locationAware(overrideFile));
        for (VolumeConfigLayer layer : mostSpecificFirst(volumeLayers)) {
            merged = merged.withFallback(layer.config());
        }
        return merged
                .withFallback(locationAware(baseFile))
                .withFallback(defaults)
                .resolve();
    }

    /** Whether any container layer sets this path, which is what makes its value the container's. */
    public boolean declares(String path) {
        return environment.hasPath(path) || overrideFile.hasPath(path) || baseFile.hasPath(path);
    }

    /**
     * Discovery reports layers weakest first, in {@link cafe.jeffrey.shared.common.config.ConfigScope}
     * order; a fallback chain wants the opposite.
     */
    private static List<VolumeConfigLayer> mostSpecificFirst(List<VolumeConfigLayer> volumeLayers) {
        List<VolumeConfigLayer> reversed = new ArrayList<>(volumeLayers);
        Collections.reverse(reversed);
        return reversed;
    }

    /**
     * A file layer minus the location settings when the environment names one of its own.
     * {@code jeffrey-home} and {@code workspaces-dir} are mutually exclusive, so the winning layer
     * has to take the pair as a unit — otherwise a file that picks one and an environment that
     * picks the other produce a config that fails validation instead of one overriding the other.
     */
    private Config locationAware(Config file) {
        if (environment.hasPath(ConfigPaths.JEFFREY_HOME) || environment.hasPath(ConfigPaths.WORKSPACES_DIR)) {
            return file
                    .withoutPath(ConfigPaths.JEFFREY_HOME)
                    .withoutPath(ConfigPaths.WORKSPACES_DIR);
        }
        return file;
    }
}
