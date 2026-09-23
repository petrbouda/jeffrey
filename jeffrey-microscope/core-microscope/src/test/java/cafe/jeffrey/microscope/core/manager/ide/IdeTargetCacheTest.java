/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.manager.ide;

import cafe.jeffrey.microscope.persistence.api.IdeTargetLink;
import cafe.jeffrey.microscope.persistence.api.IdeTargetsRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdeTargetCacheTest {

    private static final String PROFILE_ID = "profile-1";
    private static final String PROJECT_ID = "loc-hash";
    private static final int PORT = 63342;
    private static final long PID = 9688L;

    /**
     * An in-memory stand-in for the store. Real enough for what these tests are about — what survives
     * a restart — and it counts reads, which is how "the map answers the jumps" is checked.
     */
    private static final class InMemoryRepository implements IdeTargetsRepository {

        private final Map<String, IdeTargetLink> rows = new HashMap<>();
        private int reads;

        @Override
        public void save(String profileId, IdeTargetLink link) {
            rows.put(profileId, link);
        }

        @Override
        public Optional<IdeTargetLink> find(String profileId) {
            reads++;
            return Optional.ofNullable(rows.get(profileId));
        }

        @Override
        public void delete(String profileId) {
            rows.remove(profileId);
        }
    }

    private static IdeTarget target() {
        return new IdeTarget(PORT, PROJECT_ID, "IntelliJ IDEA", "order-service", "/code/order", PID);
    }

    /**
     * The whole point: a link chosen once is still there after Jeffrey restarts, which the second
     * cache stands for.
     */
    @Nested
    class SurvivingARestart {

        @Test
        void aLinkOutlivesTheCacheThatStoredIt() {
            InMemoryRepository repository = new InMemoryRepository();
            new IdeTargetCache(repository).put(PROFILE_ID, target());

            IdeTarget restored = new IdeTargetCache(repository).get(PROFILE_ID);

            assertNotNull(restored);
            assertEquals(PROJECT_ID, restored.projectId());
            assertEquals("order-service", restored.projectName());
            assertEquals("IntelliJ IDEA", restored.ideName());
            assertEquals("/code/order", restored.basePath());
        }

        @Test
        void aRestoredLinkCarriesNoPortOrPid() {
            // Both described a process that has since restarted. Discovery re-resolves them; storing
            // them would restore a stale port and spend the first jump failing against it.
            InMemoryRepository repository = new InMemoryRepository();
            new IdeTargetCache(repository).put(PROFILE_ID, target());

            IdeTarget restored = new IdeTargetCache(repository).get(PROFILE_ID);

            assertEquals(0, restored.port());
            assertEquals(0, restored.pid());
        }

        @Test
        void aDisconnectedLinkDoesNotComeBack() {
            InMemoryRepository repository = new InMemoryRepository();
            IdeTargetCache cache = new IdeTargetCache(repository);
            cache.put(PROFILE_ID, target());

            cache.clear(PROFILE_ID);

            assertNull(new IdeTargetCache(repository).get(PROFILE_ID));
        }
    }

    @Nested
    class ReadingWithinOneRun {

        @Test
        void theStoreIsReadOnceAndThenTheMapAnswers() {
            InMemoryRepository repository = new InMemoryRepository();
            new IdeTargetCache(repository).put(PROFILE_ID, target());
            IdeTargetCache cache = new IdeTargetCache(repository);

            cache.get(PROFILE_ID);
            cache.get(PROFILE_ID);
            cache.get(PROFILE_ID);

            assertEquals(1, repository.reads);
        }

        @Test
        void aTargetJustPutIsReadWithoutTouchingTheStore() {
            InMemoryRepository repository = new InMemoryRepository();
            IdeTargetCache cache = new IdeTargetCache(repository);

            cache.put(PROFILE_ID, target());

            assertEquals(PORT, cache.get(PROFILE_ID).port());
            assertEquals(0, repository.reads);
        }

        @Test
        void aReresolvedPortIsAnsweredFromMemory() {
            // What reresolve does after an IDE restart: same window, new port.
            InMemoryRepository repository = new InMemoryRepository();
            IdeTargetCache cache = new IdeTargetCache(repository);
            cache.put(PROFILE_ID, target());

            cache.put(PROFILE_ID, new IdeTarget(
                    63344, PROJECT_ID, "IntelliJ IDEA", "order-service", "/code/order", 4821L));

            assertEquals(63344, cache.get(PROFILE_ID).port());
        }
    }

    @Nested
    class Arguments {

        @Test
        void aMissingProfileIsNeitherStoredNorRead() {
            InMemoryRepository repository = new InMemoryRepository();
            IdeTargetCache cache = new IdeTargetCache(repository);

            cache.put(null, target());
            cache.put(PROFILE_ID, null);
            cache.clear(null);

            assertNull(cache.get(null));
            assertTrue(repository.rows.isEmpty());
        }
    }
}
