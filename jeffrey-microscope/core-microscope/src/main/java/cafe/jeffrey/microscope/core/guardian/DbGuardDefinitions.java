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

package cafe.jeffrey.microscope.core.guardian;

import cafe.jeffrey.microscope.core.guardian.GuardSpecJson.ParsedSpec;
import cafe.jeffrey.microscope.persistence.api.GuardianGroupSetting;
import cafe.jeffrey.microscope.persistence.api.GuardianGuard;
import cafe.jeffrey.microscope.persistence.api.GuardianGuardRepository;
import cafe.jeffrey.profile.guardian.definition.GuardDefinition;
import cafe.jeffrey.profile.guardian.definition.GuardDefinitions;
import cafe.jeffrey.profile.guardian.definition.GuardPreconditions;
import cafe.jeffrey.profile.guardian.guard.GroupKind;
import cafe.jeffrey.profile.guardian.guard.Guard;
import cafe.jeffrey.profile.guardian.traverse.MatchingType;
import cafe.jeffrey.profile.guardian.traverse.ResultType;
import cafe.jeffrey.profile.guardian.traverse.TargetFrameType;

import java.util.List;

/**
 * Loads guard definitions from the central database (the {@code guardian_guards} and
 * {@code guardian_group_settings} tables) and maps the raw rows into the typed domain model the
 * Guardian engine consumes. Queries live on each call so edits made through the UI take effect on the
 * next Guardian run.
 */
public class DbGuardDefinitions implements GuardDefinitions {

    private final GuardianGuardRepository repository;

    public DbGuardDefinitions(GuardianGuardRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<GuardDefinition> all() {
        return repository.findAll().stream()
                .filter(GuardianGuard::enabled)
                .map(DbGuardDefinitions::toDefinition)
                .toList();
    }

    @Override
    public long minSamples(GroupKind group) {
        return repository.findAllGroupSettings().stream()
                .filter(setting -> setting.groupKind().equals(group.name()))
                .mapToLong(GuardianGroupSetting::minSamples)
                .findFirst()
                .orElse(0L);
    }

    private static GuardDefinition toDefinition(GuardianGuard guard) {
        ParsedSpec spec = GuardSpecJson.parseSpec(guard.matcherSpec());
        GuardPreconditions preconditions = GuardSpecJson.parsePreconditions(guard.preconditions());
        return new GuardDefinition(
                guard.guardId(),
                guard.name(),
                guard.enabled(),
                guard.builtIn(),
                GroupKind.valueOf(guard.groupKind()),
                Guard.Category.valueOf(guard.category()),
                ResultType.valueOf(guard.resultType()),
                TargetFrameType.valueOf(guard.targetFrame()),
                MatchingType.valueOf(guard.matchingType()),
                guard.infoThreshold(),
                guard.warningThreshold(),
                spec.anchor(),
                spec.traversal(),
                preconditions,
                guard.summaryNoun(),
                guard.explanation(),
                guard.solution());
    }
}
