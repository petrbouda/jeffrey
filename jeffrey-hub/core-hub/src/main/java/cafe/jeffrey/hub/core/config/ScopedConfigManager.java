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

import cafe.jeffrey.hub.model.config.ScopedConfig;
import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.hub.persistence.api.ScopedConfigRepository;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ConfigType;
import cafe.jeffrey.shared.common.config.ContentDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores configuration values and keeps each scope's published file in step with them.
 *
 * <p>A change publishes immediately, in the same call, so a value saved in the UI reaches the
 * volume before the next JVM starts rather than up to a scheduler period later. The synchronizer
 * job exists for what that cannot cover — a hub that was down, a volume that was not mounted — and
 * both paths do the same idempotent thing, so their order never matters.</p>
 *
 * <p>A publish that fails is logged, not thrown: the value is stored, the caller's edit succeeded,
 * and the job will carry the file. Failing the call would tell an operator their change was lost
 * when it was not.</p>
 */
public class ScopedConfigManager {

    private static final Logger LOG = LoggerFactory.getLogger(ScopedConfigManager.class);

    private final Clock clock;
    private final ScopedConfigRepository repository;
    private final ScopeDirectories scopeDirectories;
    private final ScopedConfigPublisher publisher;

    public ScopedConfigManager(
            Clock clock,
            ScopedConfigRepository repository,
            ScopeDirectories scopeDirectories,
            ScopedConfigPublisher publisher) {

        this.clock = clock;
        this.repository = repository;
        this.scopeDirectories = scopeDirectories;
        this.publisher = publisher;
    }

    /** Stores one value and republishes its scope. */
    public ScopedConfig upsert(ScopedConfigKey key, ConfigType type, String value) {
        ConfigValueValidators.validate(type, value);
        repository.upsert(new ScopedConfigEntry(key, type, value, clock.instant()));
        LOG.info("Configuration value stored: scope={} workspace_id={} project_id={} type={}",
                key.scope(), key.workspaceId(), key.projectId(), type);
        return publish(key);
    }

    /** Removes one value and republishes its scope, deleting the file when nothing is left. */
    public ScopedConfig delete(ScopedConfigKey key, ConfigType type) {
        repository.delete(key, type);
        LOG.info("Configuration value removed: scope={} workspace_id={} project_id={} type={}",
                key.scope(), key.workspaceId(), key.projectId(), type);
        return publish(key);
    }

    /** Removes everything a scope holds, for a workspace or project that is going away. */
    public void deleteAll(ScopedConfigKey key) {
        repository.deleteAll(key);
        publish(key);
    }

    /** What one scope holds, with the digest of the file it renders to. */
    public ScopedConfig find(ScopedConfigKey key) {
        return describe(key, repository.find(key));
    }

    /**
     * Everything that applies to a workspace — the global scope, the workspace's own and each of
     * its projects — in merge order, with scopes holding nothing left out.
     */
    public List<ScopedConfig> findForWorkspace(String workspaceId) {
        Map<ScopedConfigKey, List<ScopedConfigEntry>> byScope = new LinkedHashMap<>();
        for (ScopedConfigEntry entry : repository.findForWorkspace(workspaceId)) {
            byScope.computeIfAbsent(entry.key(), _ -> new ArrayList<>()).add(entry);
        }

        List<ScopedConfig> configs = new ArrayList<>();
        byScope.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().scope()))
                .forEach(entry -> configs.add(describe(entry.getKey(), entry.getValue())));
        return List.copyOf(configs);
    }

    /**
     * Renders a scope and writes it out. The digest comes from what was published rather than from
     * what was stored, so a scope whose folder is not there reports no file rather than a digest
     * for bytes that never reached the volume.
     */
    public ScopedConfig publish(ScopedConfigKey key) {
        List<ScopedConfigEntry> entries = repository.find(key);
        String content = ScopedConfigRenderer.render(entries);

        Optional<Path> scopeDir = scopeDirectories.resolve(key);
        if (scopeDir.isEmpty()) {
            LOG.debug("Nowhere to publish this scope yet: scope={} workspace_id={} project_id={}",
                    key.scope(), key.workspaceId(), key.projectId());
            return new ScopedConfig(key, entries, "");
        }

        try {
            return new ScopedConfig(key, entries, publisher.publish(scopeDir.get(), content));
        } catch (RuntimeException e) {
            // The values are stored; the synchronizer republishes. Failing here would report a
            // successful edit as lost.
            LOG.error("Failed to publish configuration, the synchronizer will retry: "
                            + "scope={} workspace_id={} project_id={}",
                    key.scope(), key.workspaceId(), key.projectId(), e);
            return new ScopedConfig(key, entries, "");
        }
    }

    private ScopedConfig describe(ScopedConfigKey key, List<ScopedConfigEntry> entries) {
        if (entries.isEmpty()) {
            return ScopedConfig.empty(key);
        }
        String content = ScopedConfigRenderer.render(entries);
        return new ScopedConfig(key, entries,
                ContentDigest.sha256Hex(content));
    }

    /** The scope a workspace's own values live at, spelled once. */
    public static ScopedConfigKey workspaceKey(String workspaceId) {
        return new ScopedConfigKey(ConfigScope.WORKSPACE, workspaceId, null);
    }
}
