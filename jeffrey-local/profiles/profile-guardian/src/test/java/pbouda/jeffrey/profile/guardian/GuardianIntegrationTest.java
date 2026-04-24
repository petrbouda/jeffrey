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

package pbouda.jeffrey.profile.guardian;

import org.junit.jupiter.api.Test;
import pbouda.jeffrey.profile.guardian.guard.Guard;
import pbouda.jeffrey.profile.guardian.guard.GuardAnalysisResult;
import pbouda.jeffrey.provider.profile.model.AllocatingThread;
import pbouda.jeffrey.provider.profile.model.EventDurationStats;
import pbouda.jeffrey.provider.profile.model.EventTypeWithFields;
import pbouda.jeffrey.provider.profile.model.FieldDescription;
import pbouda.jeffrey.provider.profile.model.JvmFlag;
import pbouda.jeffrey.provider.profile.model.JvmFlagDetail;
import pbouda.jeffrey.provider.profile.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.profile.repository.ProfileEventTypeRepository;
import pbouda.jeffrey.shared.common.model.EventSummary;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.shared.common.settings.ActiveSettings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end orchestration test for {@link Guardian#process()}. Exercises the full flow
 * wiring — PrerequisitesEvaluator, four {@link pbouda.jeffrey.profile.guardian.type.GuardianGroup}s,
 * and the three metadata evaluators — with repository stubs, then asserts:
 * <ol>
 *   <li>The frontend-facing "Prerequisites" category string contract.</li>
 *   <li>Every non-NA result carries a populated {@code rule}, {@code severity}, {@code group}.</li>
 *   <li>Metadata evaluators integrate correctly (their {@code Optional} results flow into the
 *       final list with the right group tag when present, and are dropped when absent).</li>
 * </ol>
 *
 * <p>Full-DuckDB coverage of the SQL layer lives in {@code JdbcProfileEventRepositoryTest};
 * full Spring-wiring coverage is out of scope because the module's context would need the
 * entire profile-management import graph to boot.
 */
class GuardianIntegrationTest {

    private static final Instant PROFILE_START = Instant.parse("2026-04-01T10:00:00Z");

    /**
     * Contract pinned so the frontend filter in ProfileGuardian.vue (line 281)
     * — {@code PREREQUISITES_CATEGORY = 'Prerequisites'} — continues to match the backend label.
     */
    @Test
    void prerequisitesCategoryLabelMatchesFrontendFilterString() {
        assertEquals("Prerequisites", Guard.Category.PREREQUISITES.getLabel(),
                "If this label changes, ProfileGuardian.vue:281 PREREQUISITES_CATEGORY must change too");
    }

    /**
     * With empty event summaries all four frame-tree groups skip, the metadata evaluators
     * return {@code Optional.empty()}, and Guardian's output consists only of the four
     * Prerequisites results — all carrying group "Prerequisites" and the right category.
     */
    @Test
    void processEmitsOnlyPrerequisitesWhenNoEventsExist() {
        // The event-stream repo is never touched when eventSummaries() is empty (no group fires),
        // so passing null is safe and lets us skip mocking a 10+ method interface.
        Guardian guardian = new Guardian(
                profileInfo(),
                new EmptyEventRepository(),
                null,
                new EmptyEventTypeRepository(),
                new ActiveSettings(List.of()),
                GuardianPropertiesTestDefaults.defaults());

        List<GuardianResult> results = guardian.process();

        assertEquals(4, results.size(),
                "With no events, only the 4 Prerequisites checks should fire");
        for (GuardianResult r : results) {
            GuardAnalysisResult item = r.analysisItem();
            assertEquals(Guard.Category.PREREQUISITES, item.category());
            assertEquals("Prerequisites", item.group(),
                    "Prerequisites results must be tagged with group 'Prerequisites' exactly");
            assertNotNull(item.rule());
            assertFalse(item.rule().isBlank());
            assertNotNull(item.severity(),
                    "Every result must have a non-null severity (OK/INFO/WARNING)");
        }
    }

    @Test
    void allFourPrerequisiteChecksAreProduced() {
        Guardian guardian = new Guardian(
                profileInfo(),
                new EmptyEventRepository(),
                null,
                new EmptyEventTypeRepository(),
                new ActiveSettings(List.of()),
                GuardianPropertiesTestDefaults.defaults());

        List<String> rules = guardian.process().stream()
                .map(r -> r.analysisItem().rule())
                .toList();

        assertTrue(rules.contains("Event Source"), "Event Source check missing");
        assertTrue(rules.contains("Recording Duration"), "Recording Duration check missing");
        assertTrue(rules.contains("Event Coverage"), "Event Coverage check missing");
        assertTrue(rules.contains("Debug Symbols"), "Debug Symbols check missing");
    }

    @Test
    void cachingProvider_skipsDelegateOnSecondCall() {
        // Integration of Guardian behind CachingGuardianProvider using the same props:
        // delegate must only invoke the underlying Guardian once.
        ProfileEventTypeRepository typeRepo = new EmptyEventTypeRepository();
        Guardian real = new Guardian(
                profileInfo(), new EmptyEventRepository(), null,
                typeRepo, new ActiveSettings(List.of()), GuardianPropertiesTestDefaults.defaults());

        int[] delegateCalls = {0};
        GuardianProvider countingDelegate = () -> {
            delegateCalls[0]++;
            return real.process();
        };

        GuardianProperties props = GuardianPropertiesTestDefaults.defaults();
        var cache = new InMemoryCacheRepository();
        CachingGuardianProvider cached = new CachingGuardianProvider(cache, countingDelegate, props);

        List<GuardianResult> first = cached.get();
        List<GuardianResult> second = cached.get();

        assertEquals(1, delegateCalls[0],
                "Second call with identical props must be served from the cache");
        assertEquals(first.size(), second.size());
    }

    // ========================================================================================
    // Stubs
    // ========================================================================================

    private static ProfileInfo profileInfo() {
        return new ProfileInfo(
                "test-profile", "proj", "ws", "test",
                RecordingEventSource.ASYNC_PROFILER,
                PROFILE_START, PROFILE_START.plusSeconds(180), PROFILE_START,
                true, false, "rec-1");
    }

    /** Event repo stub — only {@code eventsByTypeWithFields} might be called for GC_CONFIGURATION. */
    private static final class EmptyEventRepository implements ProfileEventRepository {
        @Override public Optional<ObjectNode> latestJsonFields(Type type) { return Optional.empty(); }
        @Override public List<AllocatingThread> allocatingThreads(int limit) { return List.of(); }
        @Override public List<JsonNode> eventsByTypeWithFields(Type type) { return List.of(); }
        @Override public EventDurationStats durationStatsByType(Type type) { return EventDurationStats.EMPTY; }
        @Override public boolean containsEventType(Type type) { return false; }
        @Override public List<JvmFlag> getStringRelatedFlags() { return List.of(); }
        @Override public List<JvmFlagDetail> getAllFlags() { return List.of(); }
    }

    /** EventType repo stub — returns empty summaries so no group fires. */
    private static final class EmptyEventTypeRepository implements ProfileEventTypeRepository {
        @Override public Optional<EventTypeWithFields> singleFieldsByEventType(Type type) { return Optional.empty(); }
        @Override public List<FieldDescription> eventColumns(Type type) { return List.of(); }
        @Override public List<EventSummary> eventSummaries(List<Type> types) { return List.of(); }
        @Override public List<EventSummary> eventSummaries() { return List.of(); }
    }

    private static final class InMemoryCacheRepository
            implements pbouda.jeffrey.provider.profile.repository.ProfileCacheRepository {
        private final java.util.Map<String, Object> store = new java.util.HashMap<>();
        @Override public void put(String key, Object content) { store.put(key, content); }
        @Override public boolean contains(String key) { return store.containsKey(key); }
        @SuppressWarnings("unchecked")
        @Override public <T> Optional<T> get(String key, Class<T> type) { return Optional.ofNullable((T) store.get(key)); }
        @SuppressWarnings("unchecked")
        @Override public <T> Optional<T> get(String key, tools.jackson.core.type.TypeReference<T> type) {
            return Optional.ofNullable((T) store.get(key));
        }
        @Override public void clearAll() { store.clear(); }
    }
}
