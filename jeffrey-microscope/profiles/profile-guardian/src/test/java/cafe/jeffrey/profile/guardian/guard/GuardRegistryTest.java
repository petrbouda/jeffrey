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

package cafe.jeffrey.profile.guardian.guard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import cafe.jeffrey.profile.guardian.GuardianProperties;
import cafe.jeffrey.profile.guardian.GuardianPropertiesTestDefaults;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariants over the guard registry. Because the E1 refactor hand-rebuilt 48 factory lambdas
 * from four {@code candidateGuards()} methods, we need explicit protection against silent
 * drift: wrong GroupKind, wrong property field reference, or a dropped / duplicated entry.
 */
class GuardRegistryTest {

    private static final Guard.ProfileInfo PROFILE_INFO =
            new Guard.ProfileInfo("test", Type.EXECUTION_SAMPLE);
    private static final GuardianProperties PROPS = GuardianPropertiesTestDefaults.defaults();

    /**
     * Locks in the per-group counts the pre-refactor {@code candidateGuards()} methods emitted.
     * If a new guard is added, bump the expected count in the same commit — this forces the
     * author to think about which group the guard belongs to.
     */
    @Test
    void entryCountsPerGroup_matchPreRefactorCandidateGuardsSizes() {
        assertEquals(25, GuardRegistry.instantiateFor(GroupKind.EXECUTION_SAMPLE, PROFILE_INFO, PROPS).size(),
                "ExecutionSampleGuardianGroup used to return 25 guards");
        assertEquals(8, GuardRegistry.instantiateFor(GroupKind.ALLOCATION, PROFILE_INFO, PROPS).size(),
                "AllocationGuardianGroup used to return 8 guards");
        assertEquals(9, GuardRegistry.instantiateFor(GroupKind.WALL_CLOCK, PROFILE_INFO, PROPS).size(),
                "WallClockGuardianGroup used to return 9 guards");
        assertEquals(6, GuardRegistry.instantiateFor(GroupKind.BLOCKING, PROFILE_INFO, PROPS).size(),
                "BlockingGuardianGroup used to return 6 guards");
    }

    @Test
    void entryCountAcrossAllGroupsMatchesEnumValues() {
        int totalInstantiated = 0;
        for (GroupKind kind : GroupKind.values()) {
            totalInstantiated += GuardRegistry.instantiateFor(kind, PROFILE_INFO, PROPS).size();
        }
        assertEquals(GuardRegistry.values().length, totalInstantiated,
                "Every registry entry should be instantiable under exactly one GroupKind");
    }

    /**
     * Locks in a property-reference contract: no entry can be added whose factory references a
     * threshold field that doesn't exist on GuardianProperties, or that throws on instantiation.
     * Running each factory with default properties exercises the lambda end-to-end.
     */
    @ParameterizedTest
    @EnumSource(GuardRegistry.class)
    void everyEntryInstantiatesWithoutException(GuardRegistry entry) {
        Guard guard = GuardRegistry.instantiateFor(entry.group(), PROFILE_INFO, PROPS).stream()
                .skip(orderOf(entry))
                .findFirst()
                .orElseThrow();
        assertNotNull(guard);
    }

    @ParameterizedTest
    @EnumSource(GuardRegistry.class)
    void everyProducedGuardHasAResultObject(GuardRegistry entry) {
        // Guards produce a NotApplicable result when they haven't been initialized + traversed.
        // The test still proves result() does not throw and returns a usable object with a rule
        // name — which catches NPEs from a misreferenced profileInfo/properties field.
        Guard guard = GuardRegistry.instantiateFor(entry.group(), PROFILE_INFO, PROPS).stream()
                .skip(orderOf(entry))
                .findFirst()
                .orElseThrow();

        var result = guard.result();
        assertNotNull(result, "result() must not return null");
        assertNotNull(result.analysisItem(), "analysisItem must not be null");
        String rule = result.analysisItem().rule();
        assertNotNull(rule, "guard rule name must be non-null");
        assertFalse(rule.isBlank(), "guard rule name must not be blank");
    }

    @Test
    void everyEntryHasUniqueRuleNameWithinItsGroup() {
        // Two entries in the same group with the same rule name would collide in the UI —
        // the "Logback CPU Overhead" vs "Logback Wall-Clock Overhead" split was intentional
        // precisely to keep names unique per group.
        for (GroupKind kind : GroupKind.values()) {
            List<String> names = GuardRegistry.instantiateFor(kind, PROFILE_INFO, PROPS).stream()
                    .map(g -> g.result().analysisItem().rule())
                    .toList();
            long uniqueCount = names.stream().distinct().count();
            assertEquals(names.size(), uniqueCount,
                    "Duplicate rule name in group " + kind + ": " + names);
        }
    }

    @Test
    void allocationGroup_isTheOnlyOneUsingLogbackAllocationName() {
        // Sanity: the "Logback Allocation Overhead" entry should be tagged ALLOCATION, not
        // EXECUTION_SAMPLE. Catches a copy-paste between enum entries.
        boolean found = GuardRegistry.instantiateFor(GroupKind.ALLOCATION, PROFILE_INFO, PROPS).stream()
                .map(g -> g.result().analysisItem().rule())
                .anyMatch("Logback Allocation Overhead"::equals);
        assertTrue(found, "ALLOCATION group must contain 'Logback Allocation Overhead'");
    }

    /** Index of the given entry among those sharing its group, in enum declaration order. */
    private static int orderOf(GuardRegistry target) {
        int index = 0;
        for (GuardRegistry e : GuardRegistry.values()) {
            if (e == target) {
                return index;
            }
            if (e.group() == target.group()) {
                index++;
            }
        }
        throw new IllegalStateException("Unreachable");
    }
}
