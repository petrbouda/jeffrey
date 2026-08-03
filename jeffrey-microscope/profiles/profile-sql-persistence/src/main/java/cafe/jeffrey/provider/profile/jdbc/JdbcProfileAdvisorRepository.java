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
            SELECT event_type, label, samples, prompt, dominant_self_pct, generated_at
            FROM advisor_prompts
            ORDER BY event_type""";

    //language=SQL
    private static final String FIND_PROMPT = """
            SELECT event_type, label, samples, prompt, dominant_self_pct, generated_at
            FROM advisor_prompts
            WHERE event_type = :event_type""";

    //language=SQL
    private static final String UPSERT_PROMPT = """
            INSERT INTO advisor_prompts (event_type, label, samples, prompt, dominant_self_pct,
                                         generated_at)
            VALUES (:event_type, :label, :samples, :prompt, :dominant_self_pct, :generated_at)
            ON CONFLICT (event_type) DO UPDATE SET
                label = EXCLUDED.label,
                samples = EXCLUDED.samples,
                prompt = EXCLUDED.prompt,
                dominant_self_pct = EXCLUDED.dominant_self_pct,
                generated_at = EXCLUDED.generated_at""";

    //language=SQL
    private static final String FIND_RECOMMENDATIONS = """
            SELECT event_type, severity, dominant_self_pct, report, patch, source_ref,
                   generated_at
            FROM advisor_recommendations
            ORDER BY event_type""";

    //language=SQL
    private static final String UPSERT_RECOMMENDATION = """
            INSERT INTO advisor_recommendations (event_type, severity, dominant_self_pct,
                                                 report, patch, source_ref, generated_at)
            VALUES (:event_type, :severity, :dominant_self_pct, :report, :patch,
                    :source_ref, :generated_at)
            ON CONFLICT (event_type) DO UPDATE SET
                severity = EXCLUDED.severity,
                dominant_self_pct = EXCLUDED.dominant_self_pct,
                report = EXCLUDED.report,
                patch = EXCLUDED.patch,
                source_ref = EXCLUDED.source_ref,
                generated_at = EXCLUDED.generated_at""";

    //language=SQL
    private static final String DELETE_ALL_PROMPTS = "DELETE FROM advisor_prompts";

    //language=SQL
    private static final String DELETE_ALL_RECOMMENDATIONS = "DELETE FROM advisor_recommendations";

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
                .addValue("prompt", prompt.prompt())
                .addValue("dominant_self_pct", prompt.dominantSelfPct())
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
                .addValue("dominant_self_pct", recommendation.dominantSelfPct())
                .addValue("report", recommendation.report())
                .addValue("patch", recommendation.patch())
                .addValue("source_ref", recommendation.sourceRef())
                .addValue("generated_at", Timestamp.from(recommendation.generatedAt()));

        databaseClient.insert(StatementLabel.UPSERT_ADVISOR_RECOMMENDATION, UPSERT_RECOMMENDATION, params);
    }

    @Override
    public void deleteAll() {
        MapSqlParameterSource noParams = new MapSqlParameterSource();
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
                rs.getString("prompt"),
                rs.getDouble("dominant_self_pct"),
                instant(rs, "generated_at"));
    }

    private static RowMapper<AdvisorRecommendationRow> recommendationMapper() {
        return (rs, _) -> new AdvisorRecommendationRow(
                rs.getString(PARAM_EVENT_TYPE),
                rs.getString("severity"),
                rs.getDouble("dominant_self_pct"),
                rs.getString("report"),
                rs.getString("patch"),
                rs.getString("source_ref"),
                instant(rs, "generated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
