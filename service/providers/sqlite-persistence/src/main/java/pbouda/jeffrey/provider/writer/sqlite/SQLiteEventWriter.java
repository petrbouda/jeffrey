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
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.SingleThreadedEventWriter;
import pbouda.jeffrey.provider.api.model.GenerateProfile;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.writer.sqlite.repository.JdbcProfileCacheRepository;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SQLiteEventWriter implements EventWriter {

    private final List<SingleThreadedEventWriter> writers = new ArrayList<>();

    private final DataSource dataSource;
    private final int batchSize;
    private final ProfileSequences sequences;

    private GenerateProfile profile;

    public SQLiteEventWriter(DataSource dataSource, int batchSize) {
        this.dataSource = dataSource;
        this.batchSize = batchSize;
        this.sequences = new ProfileSequences();
    }

    @Override
    public void onStart(GenerateProfile profile) {
        this.profile = profile;
    }

    @Override
    public SingleThreadedEventWriter newSingleThreadedWriter() {
        JdbcWriters jdbcWriters = new JdbcWriters(dataSource, profile.profileId(), batchSize);
        SingleThreadedEventWriter eventWriter = new SQLiteSingleThreadedEventWriter(jdbcWriters, this.sequences);
        writers.add(eventWriter);
        return eventWriter;
    }

    @Override
    public ProfileCacheRepository newProfileCacheRepository() {
        return new JdbcProfileCacheRepository(profile.profileId(), new JdbcTemplate(dataSource));
    }

    @Override
    public ProfileInfo onComplete() {
        try (JdbcWriters jdbcWriters = new JdbcWriters(dataSource, profile.profileId(), batchSize)) {
            WriterResultCollector collector = new WriterResultCollector(
                    sequences,
                    jdbcWriters.eventTypes(),
                    jdbcWriters.threads());

            for (SingleThreadedEventWriter writer : writers) {
                collector.add(writer.getResult());
            }

            collector.execute();

            // TODO: Calculate Events

            return new ProfileInfo(
                    profile.profileId(),
                    profile.projectId(),
                    profile.profileName(),
                    Instant.now(),
                    profile.profilingStartEnd().start(),
                    profile.profilingStartEnd().end());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
