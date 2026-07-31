/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.microscope.persistence.api.AdvisorClaimIndexRepository;
import cafe.jeffrey.microscope.persistence.api.AdvisorClaimIndexRow;
import cafe.jeffrey.microscope.persistence.api.AdvisorFrameOccurrences;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.sql.Timestamp;
import java.util.List;

public class JdbcAdvisorClaimIndexRepository implements AdvisorClaimIndexRepository {

    private static final String PARAM_PROFILE_ID = "profile_id";
    private static final String PARAM_EVENT_TYPE = "event_type";
    private static final String PARAM_CITED_FRAME = "cited_frame";

    //language=SQL
    private static final String DELETE_FOR_PROFILE_EVENT = """
            DELETE FROM advisor_claim_index
            WHERE profile_id = :profile_id AND event_type = :event_type""";

    //language=SQL
    private static final String DELETE_FOR_PROFILE =
            "DELETE FROM advisor_claim_index WHERE profile_id = :profile_id";

    //language=SQL
    private static final String INSERT = """
            INSERT INTO advisor_claim_index (profile_id, profile_name, workspace_id, project_id, event_type,
                                             title, cited_frame, source_path, self_pct, total_pct, generated_at)
            VALUES (:profile_id, :profile_name, :workspace_id, :project_id, :event_type,
                    :title, :cited_frame, :source_path, :self_pct, :total_pct, :generated_at)""";

    //language=SQL
    private static final String FIND_RECURRING = """
            SELECT cited_frame,
                   COUNT(DISTINCT project_id) AS project_count,
                   COUNT(*)                   AS claim_count,
                   MAX(self_pct)              AS peak_self_pct
            FROM advisor_claim_index
            GROUP BY cited_frame
            HAVING COUNT(DISTINCT project_id) >= :min_projects
            ORDER BY project_count DESC, peak_self_pct DESC
            LIMIT :limit""";

    //language=SQL
    private static final String FIND_BY_FRAME = """
            SELECT profile_id, profile_name, workspace_id, project_id, event_type, title, cited_frame,
                   source_path, self_pct, total_pct, generated_at
            FROM advisor_claim_index
            WHERE cited_frame = :cited_frame
            ORDER BY self_pct DESC""";

    //language=SQL
    private static final String COUNT_PROJECTS =
            "SELECT COUNT(DISTINCT project_id) FROM advisor_claim_index";

    private final DatabaseClient databaseClient;

    public JdbcAdvisorClaimIndexRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.ADVISOR);
    }

    @Override
    public void replaceForProfile(String profileId, String eventType, List<AdvisorClaimIndexRow> claims) {
        MapSqlParameterSource key = new MapSqlParameterSource()
                .addValue(PARAM_PROFILE_ID, profileId)
                .addValue(PARAM_EVENT_TYPE, eventType);
        databaseClient.delete(StatementLabel.DELETE_ADVISOR_CLAIM_INDEX, DELETE_FOR_PROFILE_EVENT, key);

        for (AdvisorClaimIndexRow claim : claims) {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue(PARAM_PROFILE_ID, claim.profileId())
                    .addValue("profile_name", claim.profileName())
                    .addValue("workspace_id", claim.workspaceId())
                    .addValue("project_id", claim.projectId())
                    .addValue(PARAM_EVENT_TYPE, claim.eventType())
                    .addValue("title", claim.title())
                    .addValue(PARAM_CITED_FRAME, claim.citedFrame())
                    .addValue("source_path", claim.sourcePath())
                    .addValue("self_pct", claim.selfPct())
                    .addValue("total_pct", claim.totalPct())
                    .addValue("generated_at", Timestamp.from(claim.generatedAt()));

            databaseClient.insert(StatementLabel.INSERT_ADVISOR_CLAIM_INDEX, INSERT, params);
        }
    }

    @Override
    public void deleteForProfile(String profileId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_PROFILE_ID, profileId);

        databaseClient.delete(StatementLabel.DELETE_ADVISOR_CLAIM_INDEX, DELETE_FOR_PROFILE, params);
    }

    @Override
    public List<AdvisorFrameOccurrences> findRecurringFrames(int minProjects, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("min_projects", minProjects)
                .addValue("limit", limit);

        return databaseClient.query(
                StatementLabel.FIND_ADVISOR_RECURRING_FRAMES, FIND_RECURRING, params,
                (rs, _) -> new AdvisorFrameOccurrences(
                        rs.getString(PARAM_CITED_FRAME),
                        rs.getLong("project_count"),
                        rs.getLong("claim_count"),
                        rs.getDouble("peak_self_pct")));
    }

    @Override
    public List<AdvisorClaimIndexRow> findByFrame(String citedFrame) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_CITED_FRAME, citedFrame);

        return databaseClient.query(
                StatementLabel.FIND_ADVISOR_CLAIM_OCCURRENCES, FIND_BY_FRAME, params, mapper());
    }

    @Override
    public long countAnalyzedProjects() {
        return databaseClient.queryLong(
                StatementLabel.COUNT_ADVISOR_ANALYZED_PROJECTS, COUNT_PROJECTS, new MapSqlParameterSource());
    }

    private static RowMapper<AdvisorClaimIndexRow> mapper() {
        return (rs, _) -> new AdvisorClaimIndexRow(
                rs.getString(PARAM_PROFILE_ID),
                rs.getString("profile_name"),
                rs.getString("workspace_id"),
                rs.getString("project_id"),
                rs.getString(PARAM_EVENT_TYPE),
                rs.getString("title"),
                rs.getString(PARAM_CITED_FRAME),
                rs.getString("source_path"),
                rs.getDouble("self_pct"),
                rs.getDouble("total_pct"),
                rs.getTimestamp("generated_at").toInstant());
    }
}
