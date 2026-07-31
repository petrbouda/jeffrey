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

package cafe.jeffrey.performance.analyst.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Instant;
import java.util.List;

public class JdbcRecommendationClaimRepository implements RecommendationClaimRepository {

    //language=SQL
    private static final String DELETE_FOR_RECORDING = """
            DELETE FROM recommendation_claims
            WHERE recording_id = :recording_id AND event_type = :event_type""";

    //language=SQL
    private static final String INSERT = """
            INSERT INTO recommendation_claims
                (recording_id, event_type, hub_id, workspace_id, project_id, project_name, title,
                 cited_frame, source_path, grounded, source_found, self_pct, total_pct, generated_at)
            VALUES (:recording_id, :event_type, :hub_id, :workspace_id, :project_id, :project_name, :title,
                    :cited_frame, :source_path, :grounded, :source_found, :self_pct, :total_pct, :generated_at)""";

    //language=SQL
    private static final String SELECT_BY_RECORDING = """
            SELECT recording_id, event_type, hub_id, workspace_id, project_id, project_name, title,
                   cited_frame, source_path, grounded, source_found, self_pct, total_pct, generated_at
            FROM recommendation_claims
            WHERE recording_id = :recording_id
            ORDER BY grounded DESC, self_pct DESC""";

    //language=SQL
    private static final String SELECT_BY_FRAME = """
            SELECT recording_id, event_type, hub_id, workspace_id, project_id, project_name, title,
                   cited_frame, source_path, grounded, source_found, self_pct, total_pct, generated_at
            FROM recommendation_claims
            WHERE cited_frame = :cited_frame AND grounded = 1
            ORDER BY self_pct DESC""";

    // Only grounded claims take part: an invented frame that happened to be invented twice is not a
    // fleet-wide pattern, and letting it group would make the rollup less trustworthy than the reports.
    //language=SQL
    private static final String SELECT_RECURRING = """
            SELECT cited_frame,
                   COUNT(DISTINCT project_id) AS project_count,
                   COUNT(*) AS occurrence_count,
                   MAX(self_pct) AS peak_self_pct
            FROM recommendation_claims
            WHERE grounded = 1 AND project_id IS NOT NULL
            GROUP BY cited_frame
            HAVING COUNT(DISTINCT project_id) >= :min_projects
            ORDER BY project_count DESC, peak_self_pct DESC
            LIMIT :limit""";

    //language=SQL
    private static final String COUNT_ANALYZED_PROJECTS = """
            SELECT COUNT(DISTINCT project_id) AS project_count
            FROM recommendation_claims
            WHERE grounded = 1 AND project_id IS NOT NULL""";

    private final DatabaseClient databaseClient;

    public JdbcRecommendationClaimRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.AI_PROMPTS);
    }

    @Override
    public void replaceForRecording(String recordingId, String eventType, List<StoredClaim> claims) {
        MapSqlParameterSource deleteParams = new MapSqlParameterSource()
                .addValue("recording_id", recordingId)
                .addValue("event_type", eventType);
        databaseClient.update(StatementLabel.DELETE_AI_CLAIMS_FOR_RECORDING, DELETE_FOR_RECORDING, deleteParams);

        for (StoredClaim claim : claims) {
            databaseClient.update(StatementLabel.INSERT_AI_CLAIM, INSERT, insertParams(claim));
        }
    }

    @Override
    public List<StoredClaim> findByRecording(String recordingId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("recording_id", recordingId);
        return databaseClient.query(
                StatementLabel.FIND_AI_CLAIMS_BY_RECORDING, SELECT_BY_RECORDING, params, claimMapper());
    }

    @Override
    public List<FleetPattern> findRecurringPatterns(int minProjects, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("min_projects", minProjects)
                .addValue("limit", limit);
        return databaseClient.query(
                StatementLabel.FIND_AI_RECURRING_PATTERNS, SELECT_RECURRING, params, patternMapper());
    }

    @Override
    public List<StoredClaim> findByFrame(String citedFrame) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("cited_frame", citedFrame);
        return databaseClient.query(
                StatementLabel.FIND_AI_CLAIMS_BY_FRAME, SELECT_BY_FRAME, params, claimMapper());
    }

    @Override
    public int countAnalyzedProjects() {
        return databaseClient.query(
                        StatementLabel.COUNT_AI_ANALYZED_PROJECTS, COUNT_ANALYZED_PROJECTS,
                        new MapSqlParameterSource(), (rs, _) -> rs.getInt("project_count"))
                .stream()
                .findFirst()
                .orElse(0);
    }

    private static MapSqlParameterSource insertParams(StoredClaim claim) {
        return new MapSqlParameterSource()
                .addValue("recording_id", claim.recordingId())
                .addValue("event_type", claim.eventType())
                .addValue("hub_id", claim.hubId())
                .addValue("workspace_id", claim.workspaceId())
                .addValue("project_id", claim.projectId())
                .addValue("project_name", claim.projectName())
                .addValue("title", claim.title())
                .addValue("cited_frame", claim.citedFrame())
                .addValue("source_path", claim.sourcePath())
                .addValue("grounded", claim.grounded() ? 1 : 0)
                .addValue("source_found", claim.sourceFound() ? 1 : 0)
                .addValue("self_pct", claim.selfPct())
                .addValue("total_pct", claim.totalPct())
                .addValue("generated_at", claim.generatedAt().toEpochMilli());
    }

    private static RowMapper<StoredClaim> claimMapper() {
        return (rs, _) -> new StoredClaim(
                rs.getString("recording_id"),
                rs.getString("event_type"),
                rs.getString("hub_id"),
                rs.getString("workspace_id"),
                rs.getString("project_id"),
                rs.getString("project_name"),
                rs.getString("title"),
                rs.getString("cited_frame"),
                rs.getString("source_path"),
                rs.getInt("grounded") == 1,
                rs.getInt("source_found") == 1,
                rs.getDouble("self_pct"),
                rs.getDouble("total_pct"),
                Instant.ofEpochMilli(rs.getLong("generated_at")));
    }

    private static RowMapper<FleetPattern> patternMapper() {
        return (rs, _) -> new FleetPattern(
                rs.getString("cited_frame"),
                rs.getInt("project_count"),
                rs.getInt("occurrence_count"),
                rs.getDouble("peak_self_pct"));
    }
}
