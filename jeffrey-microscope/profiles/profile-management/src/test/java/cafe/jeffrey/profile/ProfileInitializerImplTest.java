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

package cafe.jeffrey.profile;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.action.ProfileDataInitializer;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingEventParserResolver;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the ordering that profile initialization depends on but that no other test can see: the
 * trace tables are derived from the events, so the derivation has to run after the writer has
 * finished and before anything can read a trace.
 * <p>
 * Deliberately a wiring test rather than an end-to-end one. What the repository tests cannot cover
 * is that {@code derive()} is called at all -- a deletion there would leave every trace query
 * returning nothing, with every other test still green.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileInitializerImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    ProfileRepositories profileRepositories;

    @Mock
    DatabaseManager databaseManager;

    @Mock
    RecordingEventParserResolver recordingEventParserResolver;

    @Mock
    EventWriter.Factory eventWriterFactory;

    @Mock
    ProfileDataInitializer profileDataInitializer;

    @Mock
    TraceRepository traceRepository;

    @Mock
    EventWriter eventWriter;

    @Mock
    RecordingEventParser recordingEventParser;

    private ProfileInitializerImpl initializer(ProfileInfo profileInfo) {
        DataSource dataSource = mock(DataSource.class);
        DatabaseLease lease = mock(DatabaseLease.class);
        when(lease.dataSource()).thenReturn(dataSource);
        when(databaseManager.acquire(profileInfo.id())).thenReturn(lease);

        when(eventWriterFactory.create(any(), any())).thenReturn(eventWriter);
        when(recordingEventParserResolver.resolve(any())).thenReturn(recordingEventParser);
        when(profileRepositories.newTraceRepository(dataSource)).thenReturn(traceRepository);

        // The re-cluster and checkpoint steps at the tail run through the infrastructure client.
        DatabaseClientProvider clientProvider = mock(DatabaseClientProvider.class);
        when(clientProvider.provide(any())).thenReturn(mock(DatabaseClient.class));
        when(profileRepositories.databaseClientProvider(dataSource)).thenReturn(clientProvider);

        return new ProfileInitializerImpl(
                profileRepositories,
                databaseManager,
                recordingEventParserResolver,
                eventWriterFactory,
                _ -> mock(ProfileManager.class),
                profileDataInitializer,
                CLOCK);
    }

    @Test
    @DisplayName("derives the trace tables once the events are written")
    void derivesTracesAfterParsing() {
        ProfileInfo profileInfo = mock(ProfileInfo.class);
        when(profileInfo.id()).thenReturn("profile-1");

        initializer(profileInfo).initialize(profileInfo, null, Path.of("recording.jfr"));

        // Before the writer completes there is nothing to derive from; after the data initializer
        // the pre-computed views would have been built against tables that were still empty.
        InOrder inOrder = inOrder(eventWriter, traceRepository, profileDataInitializer);
        inOrder.verify(eventWriter).onComplete();
        inOrder.verify(traceRepository).derive();
        inOrder.verify(profileDataInitializer).initialize(any());
    }
}
