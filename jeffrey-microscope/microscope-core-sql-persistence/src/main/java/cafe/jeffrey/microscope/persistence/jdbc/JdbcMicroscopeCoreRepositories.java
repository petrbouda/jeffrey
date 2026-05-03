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

package cafe.jeffrey.microscope.persistence.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.microscope.persistence.api.*;
import cafe.jeffrey.shared.common.model.ProfileInfo;
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

    private final DatabaseClientProvider databaseClientProvider;
    private final DatabaseClient profilesDatabaseClient;
    private final RecordingTagsRepository recordingTagsRepository;
    private final Clock clock;

    public JdbcMicroscopeCoreRepositories(DatabaseClientProvider databaseClientProvider, Clock clock) {
        this.databaseClientProvider = databaseClientProvider;
        this.profilesDatabaseClient = databaseClientProvider.provide(GroupLabel.PROFILES);
        this.recordingTagsRepository = new JdbcRecordingTagsRepository(databaseClientProvider);
        this.clock = clock;
    }

    @Override
    public ProfileRepository newProfileRepository(String profileId) {
        return new JdbcProfileRepository(profileId, databaseClientProvider);
    }

    @Override
    public RecordingRepository newRecordingRepository(String projectId) {
        return new JdbcRecordingRepository(projectId, databaseClientProvider);
    }

    @Override
    public RecordingTagsRepository recordingTagsRepository() {
        return recordingTagsRepository;
    }

    @Override
    public List<ProfileInfo> findAllProfilesByProject(String projectId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return profilesDatabaseClient.query(
                StatementLabel.FIND_ALL_PROFILES, SELECT_ALL_PROFILES, paramSource, Mappers.profileInfoMapper());
    }
}
