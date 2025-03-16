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
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.SingleThreadedEventWriter;
import pbouda.jeffrey.provider.api.model.GenerateProfile;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.writer.sqlite.calculated.EventCalculator;
import pbouda.jeffrey.provider.writer.sqlite.calculated.NativeLeakEventCalculator;
import pbouda.jeffrey.provider.writer.sqlite.internal.InternalProfileRepository;
import pbouda.jeffrey.provider.writer.sqlite.repository.JdbcProfileCacheRepository;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SQLiteEventWriter implements EventWriter {

    private final List<SQLiteSingleThreadedEventWriter> writers = new ArrayList<>();

    private final DataSource dataSource;
    private final int batchSize;
    private final ProfileSequences sequences;
    private final InternalProfileRepository profileRepository;

    private GenerateProfile profile;

    public SQLiteEventWriter(DataSource dataSource, int batchSize) {
        this.dataSource = dataSource;
        this.batchSize = batchSize;
        this.sequences = new ProfileSequences();
        this.profileRepository = new InternalProfileRepository(dataSource);
    }

    @Override
    public void onStart(GenerateProfile profile) {
        this.profile = profile;
        var insertProfile = new InternalProfileRepository.InsertProfile(
                profile.projectId(),
                profile.profileId(),
                profile.profileName(),
                profile.profilingStartEnd().start(),
                profile.profilingStartEnd().end(),
                Instant.now());

        profileRepository.insertProfile(insertProfile);
    }

    @Override
    public SingleThreadedEventWriter newSingleThreadedWriter() {
        JdbcWriters jdbcWriters = new JdbcWriters(dataSource, profile.profileId(), batchSize);
        SQLiteSingleThreadedEventWriter eventWriter = new SQLiteSingleThreadedEventWriter(jdbcWriters, this.sequences);
        writers.add(eventWriter);
        return eventWriter;
    }

    @Override
    public ProfileCacheRepository newProfileCacheRepository() {
        return new JdbcProfileCacheRepository(profile.profileId(), new JdbcTemplate(dataSource));
    }

    @Override
    public ProfileInfo onComplete() {
        String profileId = profile.profileId();

        try (JdbcWriters jdbcWriters = new JdbcWriters(dataSource, profileId, batchSize)) {
            WriterResultCollector collector = new WriterResultCollector(
                    jdbcWriters.eventTypes(),
                    jdbcWriters.threads());

            for (SQLiteSingleThreadedEventWriter writer : writers) {
                collector.add(writer.getResult());
            }

            collector.execute();

            // Calculate artificial events and write them to the database
            resolveEventCalculators(jdbcWriters).stream()
                    .filter(EventCalculator::applicable)
                    .forEach(EventCalculator::publish);

            this.profileRepository.initializeProfile(profile.projectId(), profileId);

            return new ProfileInfo(
                    profileId,
                    profile.projectId(),
                    profile.profileName(),
                    profile.profilingStartEnd().start(),
                    profile.profilingStartEnd().end(),
                    Instant.now(),
                    false);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Cannot properly complete the initialization of the profile: profile_id=" + profileId, e);
        } finally {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("PRAGMA wal_checkpoint(TRUNCATE);");
        }
    }

    private List<EventCalculator> resolveEventCalculators(JdbcWriters jdbcWriters) {
        NativeLeakEventCalculator nativeLeaks = new NativeLeakEventCalculator(
                profile.profileId(), jdbcWriters.eventTypes());

        return List.of(nativeLeaks);
    }
}
