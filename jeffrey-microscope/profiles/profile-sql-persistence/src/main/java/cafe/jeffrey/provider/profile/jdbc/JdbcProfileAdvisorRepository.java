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

package cafe.jeffrey.provider.profile.jdbc;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.provider.profile.api.AdvisorClaimRow;
import cafe.jeffrey.provider.profile.api.AdvisorPromptRow;
import cafe.jeffrey.provider.profile.api.AdvisorRecommendationRow;
import cafe.jeffrey.provider.profile.api.ProfileAdvisorRepository;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcProfileAdvisorRepository implements ProfileAdvisorRepository {

    private static final String PARAM_EVENT_TYPE = "event_type";

    //language=SQL
    private static final String FIND_PROMPTS = """
            SELECT event_type, label, samples, markdown, frame_index, generated_at
            FROM advisor_prompts
            ORDER BY event_type""";

    //language=SQL
    private static final String FIND_PROMPT = """
            SELECT event_type, label, samples, markdown, frame_index, generated_at
            FROM advisor_prompts
            WHERE event_type = :event_type""";

    //language=SQL
    private static final String UPSERT_PROMPT = """
            INSERT INTO advisor_prompts (event_type, label, samples, markdown, frame_index, generated_at)
            VALUES (:event_type, :label, :samples, :markdown, :frame_index, :generated_at)
            ON CONFLICT (event_type) DO UPDATE SET
                label = EXCLUDED.label,
                samples = EXCLUDED.samples,
                markdown = EXCLUDED.markdown,
                frame_index = EXCLUDED.frame_index,
                generated_at = EXCLUDED.generated_at""";

    //language=SQL
    private static final String FIND_RECOMMENDATIONS = """
            SELECT event_type, severity, recommendations, patch, source_ref, generated_at
            FROM advisor_recommendations
            ORDER BY event_type""";

    //language=SQL
    private static final String UPSERT_RECOMMENDATION = """
            INSERT INTO advisor_recommendations (event_type, severity, recommendations,
                                                 patch, source_ref, generated_at)
            VALUES (:event_type, :severity, :recommendations, :patch, :source_ref, :generated_at)
            ON CONFLICT (event_type) DO UPDATE SET
                severity = EXCLUDED.severity,
                recommendations = EXCLUDED.recommendations,
                patch = EXCLUDED.patch,
                source_ref = EXCLUDED.source_ref,
                generated_at = EXCLUDED.generated_at""";

    //language=SQL
    private static final String FIND_CLAIMS = """
            SELECT event_type, title, cited_frame, source_path, grounded, source_found,
                   self_pct, total_pct, generated_at
            FROM advisor_claims
            ORDER BY event_type, self_pct DESC""";

    //language=SQL
    private static final String DELETE_CLAIMS =
            "DELETE FROM advisor_claims WHERE event_type = :event_type";

    //language=SQL
    private static final String INSERT_CLAIM = """
            INSERT INTO advisor_claims (event_type, title, cited_frame, source_path, grounded,
                                        source_found, self_pct, total_pct, generated_at)
            VALUES (:event_type, :title, :cited_frame, :source_path, :grounded,
                    :source_found, :self_pct, :total_pct, :generated_at)""";

    //language=SQL
    private static final String DELETE_ALL_PROMPTS = "DELETE FROM advisor_prompts";

    //language=SQL
    private static final String DELETE_ALL_RECOMMENDATIONS = "DELETE FROM advisor_recommendations";

    //language=SQL
    private static final String DELETE_ALL_CLAIMS = "DELETE FROM advisor_claims";

    private final DatabaseClient databaseClient;

    public JdbcProfileAdvisorRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.ADVISOR);
    }

    @Override
    public List<AdvisorPromptRow> findPrompts() {
        return databaseClient.query(
                StatementLabel.FIND_ADVISOR_PROMPTS, FIND_PROMPTS, promptMapper());
    }

    @Override
    public Optional<AdvisorPromptRow> findPrompt(String eventType) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_EVENT_TYPE, eventType);

        return databaseClient.querySingle(
                StatementLabel.FIND_ADVISOR_PROMPTS, FIND_PROMPT, params, promptMapper());
    }

    @Override
    public void upsertPrompt(AdvisorPromptRow prompt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_EVENT_TYPE, prompt.eventType())
                .addValue("label", prompt.label())
                .addValue("samples", prompt.samples())
                .addValue("markdown", prompt.markdown())
                .addValue("frame_index", prompt.frameIndexJson())
                .addValue("generated_at", Timestamp.from(prompt.generatedAt()));

        databaseClient.insert(StatementLabel.UPSERT_ADVISOR_PROMPT, UPSERT_PROMPT, params);
    }

    @Override
    public List<AdvisorRecommendationRow> findRecommendations() {
        return databaseClient.query(
                StatementLabel.FIND_ADVISOR_RECOMMENDATIONS, FIND_RECOMMENDATIONS, recommendationMapper());
    }

    @Override
    public void upsertRecommendation(AdvisorRecommendationRow recommendation) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_EVENT_TYPE, recommendation.eventType())
                .addValue("severity", recommendation.severity())
                .addValue("recommendations", recommendation.recommendations())
                .addValue("patch", recommendation.patch())
                .addValue("source_ref", recommendation.sourceRef())
                .addValue("generated_at", Timestamp.from(recommendation.generatedAt()));

        databaseClient.insert(StatementLabel.UPSERT_ADVISOR_RECOMMENDATION, UPSERT_RECOMMENDATION, params);
    }

    @Override
    public List<AdvisorClaimRow> findClaims() {
        return databaseClient.query(StatementLabel.FIND_ADVISOR_CLAIMS, FIND_CLAIMS, claimMapper());
    }

    @Override
    public void replaceClaims(String eventType, List<AdvisorClaimRow> claims) {
        MapSqlParameterSource deleteParams = new MapSqlParameterSource()
                .addValue(PARAM_EVENT_TYPE, eventType);
        databaseClient.delete(StatementLabel.DELETE_ADVISOR_CLAIMS, DELETE_CLAIMS, deleteParams);

        for (AdvisorClaimRow claim : claims) {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue(PARAM_EVENT_TYPE, claim.eventType())
                    .addValue("title", claim.title())
                    .addValue("cited_frame", claim.citedFrame())
                    .addValue("source_path", claim.sourcePath())
                    .addValue("grounded", claim.grounded())
                    .addValue("source_found", claim.sourceFound())
                    .addValue("self_pct", claim.selfPct())
                    .addValue("total_pct", claim.totalPct())
                    .addValue("generated_at", Timestamp.from(claim.generatedAt()));

            databaseClient.insert(StatementLabel.INSERT_ADVISOR_CLAIM, INSERT_CLAIM, params);
        }
    }

    @Override
    public void deleteAll() {
        MapSqlParameterSource noParams = new MapSqlParameterSource();
        databaseClient.delete(
                StatementLabel.DELETE_ALL_ADVISOR_CLAIMS, DELETE_ALL_CLAIMS, noParams);
        databaseClient.delete(
                StatementLabel.DELETE_ALL_ADVISOR_RECOMMENDATIONS, DELETE_ALL_RECOMMENDATIONS, noParams);
        databaseClient.delete(
                StatementLabel.DELETE_ALL_ADVISOR_PROMPTS, DELETE_ALL_PROMPTS, noParams);
    }

    private static RowMapper<AdvisorPromptRow> promptMapper() {
        return (rs, _) -> new AdvisorPromptRow(
                rs.getString(PARAM_EVENT_TYPE),
                rs.getString("label"),
                rs.getLong("samples"),
                rs.getString("markdown"),
                rs.getString("frame_index"),
                instant(rs, "generated_at"));
    }

    private static RowMapper<AdvisorRecommendationRow> recommendationMapper() {
        return (rs, _) -> new AdvisorRecommendationRow(
                rs.getString(PARAM_EVENT_TYPE),
                rs.getString("severity"),
                rs.getString("recommendations"),
                rs.getString("patch"),
                rs.getString("source_ref"),
                instant(rs, "generated_at"));
    }

    private static RowMapper<AdvisorClaimRow> claimMapper() {
        return (rs, _) -> new AdvisorClaimRow(
                rs.getString(PARAM_EVENT_TYPE),
                rs.getString("title"),
                rs.getString("cited_frame"),
                rs.getString("source_path"),
                rs.getBoolean("grounded"),
                rs.getBoolean("source_found"),
                rs.getDouble("self_pct"),
                rs.getDouble("total_pct"),
                instant(rs, "generated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
