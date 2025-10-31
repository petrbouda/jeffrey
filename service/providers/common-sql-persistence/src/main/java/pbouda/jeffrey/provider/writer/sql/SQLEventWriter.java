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
import pbouda.jeffrey.provider.api.EventWriters;
import pbouda.jeffrey.provider.api.SingleThreadedEventWriter;
import pbouda.jeffrey.provider.writer.sql.calculated.EventCalculator;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class SQLEventWriter implements EventWriter {

    private final List<SQLSingleThreadedEventWriter> writers = new CopyOnWriteArrayList<>();

    private final String profileId;
    private final Supplier<EventWriters> eventWriters;
    private final ProfileSequences sequences;

    public SQLEventWriter(String profileId, Supplier<EventWriters> eventWriters) {
        this.profileId = profileId;
        this.eventWriters = eventWriters;
        this.sequences = new ProfileSequences();
    }

    @Override
    public SingleThreadedEventWriter newSingleThreadedWriter() {
        EventWriters writersProvider = eventWriters.get();
        SQLSingleThreadedEventWriter eventWriter = new SQLSingleThreadedEventWriter(profileId, writersProvider, sequences);
        writers.add(eventWriter);
        return eventWriter;
    }

    @Override
    public void onComplete() {
        try (EventWriters writersProvider = eventWriters.get()) {
            WriterResultCollector collector = new WriterResultCollector(writersProvider.eventTypes(), writersProvider.threads());

            for (SQLSingleThreadedEventWriter writer : writers) {
                collector.add(writer.getResult());
            }

            collector.combine();
        }

        /*
         * At this point, all the event types and threads from the recordings are written to the database,
         * and we can calculate the artificial events based on the existing ones.
         */
        try (EventWriters writersProvider = eventWriters.get()) {
            // Calculate artificial events and write them to the database
            resolveEventCalculators(writersProvider).stream().filter(EventCalculator::applicable).forEach(EventCalculator::publish);
        }
    }

    private List<EventCalculator> resolveEventCalculators(EventWriters writersProvider) {
        return List.of();
        // TODO: Enable NativeLeakEventCalculator again! with Specified EventDatabaseClient (SQLite and Clickhouse implementation)
//        return List.of(new NativeLeakEventCalculator(
//                profileId,
//                databaseClientProvider,
//                writersProvider.eventTypes()));
    }
}
