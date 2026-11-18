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

package cafe.jeffrey.microscope.profile.persistence.api;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileDatabasesTest {

    private static final Instant CREATED_AT = Instant.parse("2026-03-01T00:00:00Z");
    private static final ProfileInfo PROFILE = new ProfileInfo(
            "p-1", "checkout", RecordingEventSource.JFR, CREATED_AT, CREATED_AT, CREATED_AT, true, false, "rec-1");

    private final DatabaseManager databaseManager = mock(DatabaseManager.class);
    private final ProfileRepositories repositories = mock(ProfileRepositories.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final ProfileDatabases databases = new ProfileDatabases(databaseManager, repositories);

    @Nested
    class Open {

        @Test
        void opensTheDatabaseOfTheProfileById() {
            when(databaseManager.open("p-1")).thenReturn(dataSource);

            assertSame(dataSource, databases.open(PROFILE).dataSource());
        }

        /** Every repository a feature is built from reads the one database the profile was opened as. */
        @Test
        void buildsEveryRepositoryOverThatDatabase() {
            when(databaseManager.open("p-1")).thenReturn(dataSource);
            ProfileEventRepository events = mock(ProfileEventRepository.class);
            ProfileEventStreamRepository eventStream = mock(ProfileEventStreamRepository.class);
            when(repositories.newEventRepository(dataSource)).thenReturn(events);
            when(repositories.newEventStreamRepository(dataSource)).thenReturn(eventStream);

            ProfileDatabase database = databases.open(PROFILE);

            assertSame(events, database.events());
            assertSame(eventStream, database.eventStream());
        }
    }

    @Test
    void acquiresALeaseOnTheDatabaseOfTheProfile() {
        DatabaseLease lease = mock(DatabaseLease.class);
        when(databaseManager.acquire("p-1")).thenReturn(lease);

        assertSame(lease, databases.acquire(PROFILE));
        verify(databaseManager).acquire("p-1");
    }
}
