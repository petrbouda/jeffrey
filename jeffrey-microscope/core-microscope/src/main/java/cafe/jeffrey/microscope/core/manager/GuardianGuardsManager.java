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

package cafe.jeffrey.microscope.core.manager;

import cafe.jeffrey.microscope.persistence.api.GuardianGuard;
import cafe.jeffrey.microscope.persistence.api.GuardianGuardRepository;
import cafe.jeffrey.shared.common.IDGenerator;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * CRUD operations over the central Guardian guard definitions. Assigns identifiers and creation
 * timestamps for new guards and preserves the immutable {@code builtIn} flag and {@code createdAt}
 * of an existing guard across updates.
 */
public class GuardianGuardsManager {

    private final GuardianGuardRepository repository;
    private final Clock clock;

    public GuardianGuardsManager(GuardianGuardRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<GuardianGuard> list() {
        return repository.findAll();
    }

    public Optional<GuardianGuard> find(String guardId) {
        return repository.find(guardId);
    }

    /** Creates a new user-defined guard ({@code builtIn = false}) with a generated id and timestamp. */
    public GuardianGuard create(GuardianGuard draft) {
        GuardianGuard guard = withIdentity(draft, IDGenerator.generate(), false, clock.instant());
        repository.insert(guard);
        return guard;
    }

    /** Updates an existing guard, preserving its {@code builtIn} flag and original {@code createdAt}. */
    public Optional<GuardianGuard> update(String guardId, GuardianGuard draft) {
        return repository.find(guardId).map(existing -> {
            GuardianGuard guard = withIdentity(draft, guardId, existing.builtIn(), existing.createdAt());
            repository.update(guard);
            return guard;
        });
    }

    public boolean delete(String guardId) {
        if (repository.find(guardId).isEmpty()) {
            return false;
        }
        repository.delete(guardId);
        return true;
    }

    private static GuardianGuard withIdentity(GuardianGuard draft, String guardId, boolean builtIn, Instant createdAt) {
        return new GuardianGuard(
                guardId,
                draft.name(),
                draft.enabled(),
                builtIn,
                draft.eventType(),
                draft.category(),
                draft.resultType(),
                draft.targetFrame(),
                draft.matchingType(),
                draft.infoThreshold(),
                draft.warningThreshold(),
                draft.minSamples(),
                draft.matcherSpec(),
                draft.preconditions(),
                draft.summaryNoun(),
                draft.explanation(),
                draft.solution(),
                createdAt);
    }
}
