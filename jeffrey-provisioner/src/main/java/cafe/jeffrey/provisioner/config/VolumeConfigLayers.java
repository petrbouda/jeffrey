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

import cafe.jeffrey.provisioner.ProjectLayout;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ContentDigest;
import cafe.jeffrey.shared.common.config.ScopedConfigLayout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the hub-published files on the shared volume and turns them into configuration layers.
 *
 * <p>Nothing here can stop a JVM from being provisioned. The files belong to another process, may
 * be absent, may be half-written by a hub that crashed, may have been edited by hand. A file that
 * cannot be read is skipped with a warning and the run continues on what is left — losing central
 * configuration costs the defaults, not profiling.</p>
 */
public abstract class VolumeConfigLayers {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeConfigLayers.class);

    private VolumeConfigLayers() {
    }

    private static final String ORIGIN_DESCRIPTION = "hub-published configuration";

    private static final ConfigParseOptions PARSE_OPTIONS = ConfigParseOptions.defaults()
            .setIncluder(NoIncludes.INSTANCE)
            .setOriginDescription(ORIGIN_DESCRIPTION);

    /**
     * The three candidate folders, in merge order. Resolvable as soon as
     * {@code LayoutProvisioner.provisionProject} has run, which creates all three.
     */
    private static Map<ConfigScope, Path> scopeDirectories(ProjectLayout layout) {
        Map<ConfigScope, Path> directories = new EnumMap<>(ConfigScope.class);
        directories.put(ConfigScope.GLOBAL, layout.workspaces());
        directories.put(ConfigScope.WORKSPACE, layout.workspace());
        directories.put(ConfigScope.PROJECT, layout.project());
        return directories;
    }

    /** Every published file that exists and could be read, in {@link ConfigScope} order. */
    public static List<VolumeConfigLayer> discover(ProjectLayout layout) {
        List<VolumeConfigLayer> layers = new ArrayList<>();
        for (Map.Entry<ConfigScope, Path> entry : scopeDirectories(layout).entrySet()) {
            read(entry.getKey(), ScopedConfigLayout.configFile(entry.getValue())).ifPresent(layers::add);
        }
        return List.copyOf(layers);
    }

    private static Optional<VolumeConfigLayer> read(ConfigScope scope, Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        byte[] content;
        try {
            // Read once: the digest and the parsed content must describe the same bytes, which a
            // second read cannot promise while the hub may be republishing.
            content = Files.readAllBytes(file);
        } catch (IOException e) {
            LOG.warn("Skipping an unreadable configuration layer: scope={} file={} error={}",
                    scope, file, e.getMessage());
            return Optional.empty();
        }

        Config parsed;
        try {
            parsed = ConfigFactory.parseString(new String(content, StandardCharsets.UTF_8),
                    PARSE_OPTIONS);
        } catch (ConfigException e) {
            LOG.warn("Skipping an unparseable configuration layer: scope={} file={} error={}",
                    scope, file, e.getMessage());
            return Optional.empty();
        }

        Config publishable = onlyPublishablePaths(scope, parsed);
        LOG.debug("Configuration layer loaded: scope={} file={} keys={}",
                scope, file, publishable.root().keySet());
        return Optional.of(
                new VolumeConfigLayer(scope, file, ContentDigest.sha256Hex(content), publishable));
    }

    /**
     * Drops every key outside the published catalogue. The hub renders only those keys, so this
     * fires on a file edited by hand on the volume or written by a hub that knows a type this
     * provisioner does not — which is exactly when it matters, because the dropped keys are the
     * ones that would otherwise add JVM flags nobody reviewed.
     */
    private static Config onlyPublishablePaths(ConfigScope scope, Config parsed) {
        Set<String> publishable = ScopedConfigLayout.publishablePaths();
        Config filtered = parsed;
        for (String key : parsed.root().keySet()) {
            if (!publishable.contains(key)) {
                LOG.warn("Dropping a key a published configuration may not set: scope={} key={}", scope, key);
                filtered = filtered.withoutPath(key);
            }
        }
        return filtered;
    }
}
