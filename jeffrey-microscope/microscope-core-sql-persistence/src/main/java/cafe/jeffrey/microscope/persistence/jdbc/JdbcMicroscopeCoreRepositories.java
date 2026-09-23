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

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.microscope.persistence.api.*;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;
import java.util.List;

public class JdbcMicroscopeCoreRepositories implements MicroscopeCoreRepositories {

    //language=SQL
    private static final String SELECT_ALL_PROFILES =
            "SELECT * FROM profiles WHERE project_id = :project_id";

    //language=SQL
    private static final String SELECT_EVERY_PROFILE =
            "SELECT * FROM profiles ORDER BY created_at DESC";

    private final DatabaseClientProvider databaseClientProvider;
    private final DatabaseClient profilesDatabaseClient;
    private final RecordingTagsRepository recordingTagsRepository;
    private final IdeTargetsRepository ideTargetsRepository;
    private final Clock clock;

    public JdbcMicroscopeCoreRepositories(DatabaseClientProvider databaseClientProvider, Clock clock) {
        this.databaseClientProvider = databaseClientProvider;
        this.profilesDatabaseClient = databaseClientProvider.provide(GroupLabel.PROFILES);
        this.recordingTagsRepository = new JdbcRecordingTagsRepository(databaseClientProvider);
        this.ideTargetsRepository = new JdbcIdeTargetsRepository(databaseClientProvider);
        this.clock = clock;
    }

    @Override
    public ProfileRepository newProfileRepository(String profileId) {
        return new JdbcProfileRepository(profileId, databaseClientProvider);
    }

    @Override
    public RecordingRepository newRecordingRepository() {
        return new JdbcRecordingRepository(databaseClientProvider, clock);
    }

    @Override
    public RecordingTagsRepository recordingTagsRepository() {
        return recordingTagsRepository;
    }

    @Override
    public IdeTargetsRepository ideTargetsRepository() {
        return ideTargetsRepository;
    }

    @Override
    public List<ProfileInfo> findAllProfilesByProject(String projectId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return profilesDatabaseClient.query(
                StatementLabel.FIND_ALL_PROFILES, SELECT_ALL_PROFILES, paramSource, Mappers.profileInfoMapper());
    }

    @Override
    public List<ProfileInfo> findAllProfiles() {
        return profilesDatabaseClient.query(
                StatementLabel.FIND_ALL_PROFILES, SELECT_EVERY_PROFILE, Mappers.profileInfoMapper());
    }
}
