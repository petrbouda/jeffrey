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

import cafe.jeffrey.microscope.persistence.api.IdeTargetLink;
import cafe.jeffrey.microscope.persistence.api.IdeTargetsRepository;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.Optional;

public class JdbcIdeTargetsRepository implements IdeTargetsRepository {

    //language=SQL
    private static final String UPSERT = """
            INSERT INTO ide_targets (profile_id, project_id, project_name, ide_name, base_path)
            VALUES (:profile_id, :project_id, :project_name, :ide_name, :base_path)
            ON CONFLICT (profile_id) DO UPDATE SET
                project_id = EXCLUDED.project_id,
                project_name = EXCLUDED.project_name,
                ide_name = EXCLUDED.ide_name,
                base_path = EXCLUDED.base_path""";

    //language=SQL
    private static final String SELECT_ONE = """
            SELECT project_id, project_name, ide_name, base_path
            FROM ide_targets
            WHERE profile_id = :profile_id""";

    //language=SQL
    private static final String DELETE_ONE = "DELETE FROM ide_targets WHERE profile_id = :profile_id";

    private static final String PARAM_PROFILE_ID = "profile_id";

    private final DatabaseClient databaseClient;

    public JdbcIdeTargetsRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.IDE_TARGETS);
    }

    /**
     * Upserted rather than deleted-and-inserted: re-linking the same profile is the common case, and
     * a delete followed by an insert leaves a window in which the profile has no link at all.
     */
    @Override
    public void save(String profileId, IdeTargetLink link) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_PROFILE_ID, profileId)
                .addValue("project_id", link.projectId())
                .addValue("project_name", link.projectName())
                .addValue("ide_name", link.ideName())
                .addValue("base_path", link.basePath());

        databaseClient.insert(StatementLabel.UPSERT_IDE_TARGET, UPSERT, params);
    }

    @Override
    public Optional<IdeTargetLink> find(String profileId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_PROFILE_ID, profileId);

        List<IdeTargetLink> rows = databaseClient.query(
                StatementLabel.FIND_IDE_TARGET, SELECT_ONE, params,
                (rs, _) -> new IdeTargetLink(
                        rs.getString("project_id"),
                        rs.getString("project_name"),
                        rs.getString("ide_name"),
                        rs.getString("base_path")));

        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public void delete(String profileId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_PROFILE_ID, profileId);

        databaseClient.delete(StatementLabel.DELETE_IDE_TARGET, DELETE_ONE, params);
    }
}
