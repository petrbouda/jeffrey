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

package pbouda.jeffrey.provider.writer.sqlite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.SingleThreadedEventWriter;
import pbouda.jeffrey.provider.writer.sqlite.calculated.EventCalculator;
import pbouda.jeffrey.provider.writer.sqlite.calculated.NativeLeakEventCalculator;
import pbouda.jeffrey.provider.writer.sqlite.internal.InternalProfileRepository;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SQLiteEventWriter implements EventWriter {

    private final List<SQLiteSingleThreadedEventWriter> writers = new CopyOnWriteArrayList<>();

    private final DataSource dataSource;
    private final int batchSize;
    private final ProfileSequences sequences;
    private final InternalProfileRepository profileRepository;
    private final String profileId;

    public SQLiteEventWriter(String profileId, DataSource dataSource, int batchSize) {
        this.profileId = profileId;
        this.dataSource = dataSource;
        this.batchSize = batchSize;
        this.sequences = new ProfileSequences();
        this.profileRepository = new InternalProfileRepository(dataSource);
    }

    @Override
    public SingleThreadedEventWriter newSingleThreadedWriter() {
        JdbcWriters jdbcWriters = new JdbcWriters(dataSource, profileId, batchSize);
        SQLiteSingleThreadedEventWriter eventWriter = new SQLiteSingleThreadedEventWriter(jdbcWriters, this.sequences);
        writers.add(eventWriter);
        return eventWriter;
    }

    @Override
    public String onComplete() {
        try (JdbcWriters jdbcWriters = new JdbcWriters(dataSource, profileId, batchSize)) {
            WriterResultCollector collector = new WriterResultCollector(
                    jdbcWriters.eventTypes(),
                    jdbcWriters.threads());

            for (SQLiteSingleThreadedEventWriter writer : writers) {
                collector.add(writer.getResult());
            }

            collector.combine();

            // Calculate artificial events and write them to the database
            resolveEventCalculators(jdbcWriters).stream()
                    .filter(EventCalculator::applicable)
                    .forEach(EventCalculator::publish);

            // Finish the initialization of the profile
            this.profileRepository.initializeProfile(profileId);

            return profileId;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Cannot properly complete the initialization of the profile: profile_id=" + profileId, e);
        } finally {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("PRAGMA wal_checkpoint(TRUNCATE);");
        }
    }

    private List<EventCalculator> resolveEventCalculators(JdbcWriters jdbcWriters) {
        return List.of(new NativeLeakEventCalculator(profileId, jdbcWriters.eventTypes()));
    }
}
