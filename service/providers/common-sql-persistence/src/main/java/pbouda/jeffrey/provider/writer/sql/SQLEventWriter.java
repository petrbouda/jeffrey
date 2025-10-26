/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.writer.sql;

import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.SingleThreadedEventWriter;
import pbouda.jeffrey.provider.writer.sql.calculated.EventCalculator;
import pbouda.jeffrey.provider.writer.sql.calculated.NativeLeakEventCalculator;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.writer.sql.internal.InternalProfileRepository;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SQLEventWriter implements EventWriter {

    private final List<SQLSingleThreadedEventWriter> writers = new CopyOnWriteArrayList<>();

    private final DatabaseClientProvider databaseClientProvider;
    private final DatabaseClient eventWriterDatabaseClient;
    private final int batchSize;
    private final ProfileSequences sequences;
    private final InternalProfileRepository profileRepository;
    private final String profileId;

    public SQLEventWriter(String profileId, DatabaseClientProvider databaseClientProvider, int batchSize, Clock clock) {
        this.profileId = profileId;
        this.batchSize = batchSize;
        this.sequences = new ProfileSequences();
        this.databaseClientProvider = databaseClientProvider;
        this.eventWriterDatabaseClient = databaseClientProvider.provide(GroupLabel.EVENT_WRITERS);
        this.profileRepository = new InternalProfileRepository(databaseClientProvider, clock);
    }

    @Override
    public SingleThreadedEventWriter newSingleThreadedWriter() {
        JdbcWritersProvider writersProvider = new JdbcWritersProvider(eventWriterDatabaseClient, profileId, batchSize);
        SQLSingleThreadedEventWriter eventWriter = new SQLSingleThreadedEventWriter(writersProvider, this.sequences);
        writers.add(eventWriter);
        return eventWriter;
    }

    @Override
    public String onComplete() {
        try (JdbcWritersProvider writersProvider = new JdbcWritersProvider(eventWriterDatabaseClient, profileId, batchSize)) {
            WriterResultCollector collector = new WriterResultCollector(
                    writersProvider.eventTypes(),
                    writersProvider.threads());

            for (SQLSingleThreadedEventWriter writer : writers) {
                collector.add(writer.getResult());
            }

            collector.combine();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Cannot properly complete the initialization of the profile: profile_id=" + profileId, e);
        } finally {
            eventWriterDatabaseClient.walCheckpoint();
        }

        /*
         * At this point, all the event types and threads from the recordings are written to the database,
         * and we can calculate the artificial events based on the existing ones.
         */
        try (JdbcWritersProvider writersProvider = new JdbcWritersProvider(eventWriterDatabaseClient, profileId, batchSize)) {
            // Calculate artificial events and write them to the database
            resolveEventCalculators(writersProvider).stream()
                    .filter(EventCalculator::applicable)
                    .forEach(EventCalculator::publish);

            // Finish the initialization of the profile
            this.profileRepository.initializeProfile(profileId);
        } catch (Exception e) {
            throw new RuntimeException("Cannot properly calculate events: profile_id=" + profileId, e);
        } finally {
            eventWriterDatabaseClient.walCheckpoint();
        }

        return profileId;
    }

    private List<EventCalculator> resolveEventCalculators(JdbcWritersProvider writersProvider) {
        return List.of(new NativeLeakEventCalculator(
                profileId,
                databaseClientProvider,
                writersProvider.eventTypes()));
    }
}
