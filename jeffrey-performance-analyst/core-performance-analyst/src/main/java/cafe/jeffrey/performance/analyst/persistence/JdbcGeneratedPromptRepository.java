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

public class JdbcGeneratedPromptRepository implements GeneratedPromptRepository {

    //language=SQL
    private static final String SELECT_BY_RECORDING = """
            SELECT recording_id, event_type, label, samples, markdown, generated_at
            FROM generated_prompts WHERE recording_id = :recording_id ORDER BY event_type""";

    //language=SQL
    private static final String UPSERT = """
            INSERT INTO generated_prompts (recording_id, event_type, label, samples, markdown, generated_at)
            VALUES (:recording_id, :event_type, :label, :samples, :markdown, :generated_at)
            ON CONFLICT (recording_id, event_type) DO UPDATE SET
                label = :label, samples = :samples, markdown = :markdown, generated_at = :generated_at""";

    private final DatabaseClient databaseClient;

    public JdbcGeneratedPromptRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.AI_PROMPTS);
    }

    @Override
    public List<GeneratedPrompt> findByRecording(String recordingId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("recording_id", recordingId);
        return databaseClient.query(StatementLabel.FIND_AI_PROMPTS_BY_RECORDING, SELECT_BY_RECORDING, params, promptMapper());
    }

    @Override
    public void upsert(GeneratedPrompt prompt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recording_id", prompt.recordingId())
                .addValue("event_type", prompt.eventType())
                .addValue("label", prompt.label())
                .addValue("samples", prompt.samples())
                .addValue("markdown", prompt.markdown())
                .addValue("generated_at", prompt.generatedAt().toEpochMilli());
        databaseClient.update(StatementLabel.UPSERT_AI_PROMPT, UPSERT, params);
    }

    private static RowMapper<GeneratedPrompt> promptMapper() {
        return (rs, _) -> new GeneratedPrompt(
                rs.getString("recording_id"),
                rs.getString("event_type"),
                rs.getString("label"),
                rs.getLong("samples"),
                rs.getString("markdown"),
                Instant.ofEpochMilli(rs.getLong("generated_at")));
    }
}
