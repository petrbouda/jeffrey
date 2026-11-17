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

package cafe.jeffrey.microscope.persistence.jdbc;

import cafe.jeffrey.microscope.persistence.api.IdeTargetLink;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/microscope/core")
class JdbcIdeTargetsRepositoryTest {

    private static final String PROFILE_ID = "profile-1";

    private static IdeTargetLink link(String projectId, String projectName) {
        return new IdeTargetLink(projectId, projectName, "IntelliJ IDEA", "/code/" + projectName);
    }

    @Nested
    class Saving {

        @Test
        void aLinkComesBackAsItWasStored(DataSource dataSource) {
            JdbcIdeTargetsRepository repository = createRepository(dataSource);

            repository.save(PROFILE_ID, link("loc-hash", "order-service"));

            Optional<IdeTargetLink> found = repository.find(PROFILE_ID);
            assertTrue(found.isPresent());
            assertEquals("loc-hash", found.get().projectId());
            assertEquals("order-service", found.get().projectName());
            assertEquals("IntelliJ IDEA", found.get().ideName());
            assertEquals("/code/order-service", found.get().basePath());
        }

        @Test
        void relinkingReplacesRatherThanAdds(DataSource dataSource) {
            // The common case: the reader picks a different window for a profile they already linked.
            JdbcIdeTargetsRepository repository = createRepository(dataSource);

            repository.save(PROFILE_ID, link("loc-hash", "order-service"));
            repository.save(PROFILE_ID, link("other-hash", "order-service-fork"));

            Optional<IdeTargetLink> found = repository.find(PROFILE_ID);
            assertTrue(found.isPresent());
            assertEquals("other-hash", found.get().projectId());
            assertEquals("order-service-fork", found.get().projectName());
        }

        @Test
        void profilesKeepSeparateLinks(DataSource dataSource) {
            JdbcIdeTargetsRepository repository = createRepository(dataSource);

            repository.save(PROFILE_ID, link("loc-hash", "order-service"));
            repository.save("profile-2", link("other-hash", "billing"));

            assertEquals("order-service", repository.find(PROFILE_ID).orElseThrow().projectName());
            assertEquals("billing", repository.find("profile-2").orElseThrow().projectName());
        }

        @Test
        void aWindowWithNoReadableCheckoutStillLinks(DataSource dataSource) {
            // basePath and the display names are all nullable: an IDE may report no path, and the link
            // is still the reader's choice of window.
            JdbcIdeTargetsRepository repository = createRepository(dataSource);

            repository.save(PROFILE_ID, new IdeTargetLink("loc-hash", null, null, null));

            assertEquals("loc-hash", repository.find(PROFILE_ID).orElseThrow().projectId());
        }
    }

    @Nested
    class Reading {

        @Test
        void aProfileThatWasNeverLinkedHasNothing(DataSource dataSource) {
            assertTrue(createRepository(dataSource).find("never-linked").isEmpty());
        }
    }

    @Nested
    class Disconnecting {

        @Test
        void aDeletedLinkDoesNotComeBack(DataSource dataSource) {
            JdbcIdeTargetsRepository repository = createRepository(dataSource);
            repository.save(PROFILE_ID, link("loc-hash", "order-service"));

            repository.delete(PROFILE_ID);

            assertTrue(repository.find(PROFILE_ID).isEmpty());
        }

        @Test
        void deletingOneProfileLeavesTheOthers(DataSource dataSource) {
            JdbcIdeTargetsRepository repository = createRepository(dataSource);
            repository.save(PROFILE_ID, link("loc-hash", "order-service"));
            repository.save("profile-2", link("other-hash", "billing"));

            repository.delete(PROFILE_ID);

            assertTrue(repository.find(PROFILE_ID).isEmpty());
            assertTrue(repository.find("profile-2").isPresent());
        }

        @Test
        void deletingAProfileThatWasNeverLinkedIsHarmless(DataSource dataSource) {
            createRepository(dataSource).delete("never-linked");
        }
    }

    private static JdbcIdeTargetsRepository createRepository(DataSource dataSource) {
        return new JdbcIdeTargetsRepository(new DatabaseClientProvider(dataSource));
    }
}
