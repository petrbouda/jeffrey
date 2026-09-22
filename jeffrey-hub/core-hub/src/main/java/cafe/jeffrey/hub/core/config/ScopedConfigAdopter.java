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


package cafe.jeffrey.hub.core.config;

import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.hub.persistence.api.ScopedConfigRepository;
import cafe.jeffrey.shared.common.config.ConfigType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a published file back into the database when the database has nothing for that scope.
 *
 * <p>This is what makes the volume the recoverable truth for configuration, as it already is for
 * projects, instances and sessions. A hub whose home directory was ephemeral comes back to an empty
 * database; without this, the first synchronizer tick would render every scope as empty and delete
 * every workspace's configuration. Reading the files back instead costs one parse per scope and
 * turns a total loss into a no-op.</p>
 *
 * <p>Adoption is all or nothing, and only when the database holds nothing for that scope. A stored
 * value always wins: a file and a row that disagree cannot be ordered — a digest says whether two
 * things differ, never which came later — so the side that is authored is the side that is kept.</p>
 */
public class ScopedConfigAdopter {

    private static final Logger LOG = LoggerFactory.getLogger(ScopedConfigAdopter.class);

    private final Clock clock;
    private final ScopedConfigRepository repository;
    private final ScopeDirectories scopeDirectories;
    private final ScopedConfigPublisher publisher;

    public ScopedConfigAdopter(
            Clock clock,
            ScopedConfigRepository repository,
            ScopeDirectories scopeDirectories,
            ScopedConfigPublisher publisher) {

        this.clock = clock;
        this.repository = repository;
        this.scopeDirectories = scopeDirectories;
        this.publisher = publisher;
    }

    /**
     * @return whether a file was adopted, in which case the caller must not publish over it
     */
    public boolean adopt(ScopedConfigKey key) {
        if (!repository.find(key).isEmpty()) {
            return false;
        }

        Optional<Path> scopeDir = scopeDirectories.resolve(key);
        if (scopeDir.isEmpty()) {
            return false;
        }

        Optional<String> content = publisher.read(scopeDir.get());
        if (content.isEmpty()) {
            return false;
        }

        Map<ConfigType, String> values;
        try {
            values = ScopedConfigRenderer.parse(parse(content.get()));
        } catch (ConfigException e) {
            LOG.warn("Leaving an unreadable configuration file alone: scope={} workspace_id={} error={}",
                    key.scope(), key.workspaceId(), e.getMessage());
            return true;
        }

        if (values.isEmpty()) {
            return false;
        }

        for (Map.Entry<ConfigType, String> value : values.entrySet()) {
            try {
                ConfigValueValidators.validate(value.getKey(), value.getValue());
            } catch (IllegalArgumentException e) {
                LOG.warn("Leaving a configuration file alone, a value in it is not valid: "
                                + "scope={} type={} reason={}",
                        key.scope(), value.getKey(), e.getMessage());
                return true;
            }
        }

        values.forEach((type, value) ->
                repository.upsert(new ScopedConfigEntry(key, type, value, clock.instant())));
        return true;
    }

    private static Config parse(String content) {
        return ConfigFactory.parseString(content);
    }
}
