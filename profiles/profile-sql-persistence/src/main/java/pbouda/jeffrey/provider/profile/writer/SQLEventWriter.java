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

package pbouda.jeffrey.provider.profile.writer;

import pbouda.jeffrey.provider.profile.EventWriter;
import pbouda.jeffrey.provider.profile.EventWriters;
import pbouda.jeffrey.provider.profile.SingleThreadedEventWriter;
import pbouda.jeffrey.provider.profile.model.writer.EventDeduplicator;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class SQLEventWriter implements EventWriter {

    private final List<SQLSingleThreadedEventWriter> writers = new CopyOnWriteArrayList<>();

    private final Supplier<EventWriters> eventWritersFactory;
    private final EventDeduplicator deduplicator;

    public SQLEventWriter(Supplier<EventWriters> eventWritersFactory) {
        this.eventWritersFactory = eventWritersFactory;
        this.deduplicator = new EventDeduplicator();
    }

    @Override
    public SingleThreadedEventWriter newSingleThreadedWriter() {
        EventWriters writersProvider = eventWritersFactory.get();
        SQLSingleThreadedEventWriter eventWriter =
                new SQLSingleThreadedEventWriter(writersProvider, deduplicator);
        writers.add(eventWriter);
        return eventWriter;
    }

    @Override
    public void onComplete() {
        try (EventWriters writersProvider = eventWritersFactory.get()) {
            WriterResultCollector collector = new WriterResultCollector(writersProvider.eventTypes(), writersProvider.threads());

            for (SQLSingleThreadedEventWriter writer : writers) {
                collector.add(writer.getResult());
            }

            collector.combine();
        }
    }
}
