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
import cafe.jeffrey.shared.common.model.Severity;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Instant;
import java.util.List;

public class JdbcGeneratedRecommendationRepository implements GeneratedRecommendationRepository {

    // Maps the textual severity to a sortable rank so a single SQL pass can pick each recording's worst
    // recommendation (SQLite copies the bare columns from the row holding the MAX of this expression).
    private static final String SEVERITY_RANK =
            "CASE severity WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END";

    //language=SQL
    private static final String SELECT_BY_RECORDING = """
            SELECT recording_id, event_type, hub_id, workspace_id, project_id, project_name,
                   severity, recommendations, patch, generated_at
            FROM generated_recommendations WHERE recording_id = :recording_id ORDER BY event_type""";

    //language=SQL
    private static final String UPSERT = """
            INSERT INTO generated_recommendations
                (recording_id, event_type, hub_id, workspace_id, project_id, project_name,
                 severity, recommendations, patch, generated_at)
            VALUES (:recording_id, :event_type, :hub_id, :workspace_id, :project_id, :project_name,
                    :severity, :recommendations, :patch, :generated_at)
            ON CONFLICT (recording_id, event_type) DO UPDATE SET
                hub_id = :hub_id, workspace_id = :workspace_id, project_id = :project_id,
                project_name = :project_name, severity = :severity, recommendations = :recommendations,
                patch = :patch, generated_at = :generated_at""";

    //language=SQL
    private static final String FIND_TOP_BY_SEVERITY = """
            SELECT gr.recording_id, r.recording_name, gr.hub_id, gr.workspace_id, gr.project_id,
                   gr.project_name, gr.severity, gr.recommendations, gr.generated_at,
                   MAX(%s) AS sev_rank
            FROM generated_recommendations gr
            JOIN recordings r ON r.id = gr.recording_id
            GROUP BY gr.recording_id
            ORDER BY sev_rank DESC, gr.generated_at DESC
            LIMIT :limit""".formatted(SEVERITY_RANK.replace("severity", "gr.severity"));

    //language=SQL
    private static final String COUNT_BY_SEVERITY = """
            SELECT worst.severity AS severity, COUNT(*) AS cnt
            FROM (
                SELECT recording_id, severity, MAX(%s) AS sev_rank
                FROM generated_recommendations
                GROUP BY recording_id
            ) worst
            GROUP BY worst.severity""".formatted(SEVERITY_RANK);

    private final DatabaseClient databaseClient;

    public JdbcGeneratedRecommendationRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.AI_PROMPTS);
    }

    @Override
    public List<GeneratedRecommendation> findByRecording(String recordingId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("recording_id", recordingId);
        return databaseClient.query(
                StatementLabel.FIND_AI_RECOMMENDATIONS_BY_RECORDING, SELECT_BY_RECORDING, params, recommendationMapper());
    }

    @Override
    public void upsert(GeneratedRecommendation recommendation) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recording_id", recommendation.recordingId())
                .addValue("event_type", recommendation.eventType())
                .addValue("hub_id", recommendation.hubId())
                .addValue("workspace_id", recommendation.workspaceId())
                .addValue("project_id", recommendation.projectId())
                .addValue("project_name", recommendation.projectName())
                .addValue("severity", recommendation.severity().name())
                .addValue("recommendations", recommendation.recommendations())
                .addValue("patch", recommendation.patch())
                .addValue("generated_at", recommendation.generatedAt().toEpochMilli());
        databaseClient.update(StatementLabel.UPSERT_AI_RECOMMENDATION, UPSERT, params);
    }

    @Override
    public List<TopSeverityRecommendation> findTopBySeverity(int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        return databaseClient.query(
                StatementLabel.FIND_TOP_SEVERITY_RECOMMENDATIONS, FIND_TOP_BY_SEVERITY, params, topMapper());
    }

    @Override
    public List<SeverityCount> countBySeverity() {
        return databaseClient.query(
                StatementLabel.COUNT_RECOMMENDATIONS_BY_SEVERITY, COUNT_BY_SEVERITY,
                new MapSqlParameterSource(), severityCountMapper());
    }

    private static RowMapper<GeneratedRecommendation> recommendationMapper() {
        return (rs, _) -> new GeneratedRecommendation(
                rs.getString("recording_id"),
                rs.getString("event_type"),
                rs.getString("hub_id"),
                rs.getString("workspace_id"),
                rs.getString("project_id"),
                rs.getString("project_name"),
                Severity.fromString(rs.getString("severity")),
                rs.getString("recommendations"),
                rs.getString("patch"),
                Instant.ofEpochMilli(rs.getLong("generated_at")));
    }

    private static RowMapper<TopSeverityRecommendation> topMapper() {
        return (rs, _) -> new TopSeverityRecommendation(
                rs.getString("recording_id"),
                rs.getString("recording_name"),
                rs.getString("hub_id"),
                rs.getString("workspace_id"),
                rs.getString("project_id"),
                rs.getString("project_name"),
                Severity.fromString(rs.getString("severity")),
                rs.getString("recommendations"),
                Instant.ofEpochMilli(rs.getLong("generated_at")));
    }

    private static RowMapper<SeverityCount> severityCountMapper() {
        return (rs, _) -> new SeverityCount(
                Severity.fromString(rs.getString("severity")),
                rs.getInt("cnt"));
    }
}
