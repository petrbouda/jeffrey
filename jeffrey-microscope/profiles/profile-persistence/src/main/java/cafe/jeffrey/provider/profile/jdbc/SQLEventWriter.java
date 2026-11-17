/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.EventWriters;
import cafe.jeffrey.provider.profile.api.SingleThreadedEventWriter;
import cafe.jeffrey.provider.profile.api.EventDeduplicator;

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
