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

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JdbcRecordingTagsRepository implements RecordingTagsRepository {

    private final DatabaseClient databaseClient;

    public JdbcRecordingTagsRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROJECT_RECORDINGS);
    }

    @Override
    public void insert(String recordingId, Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }

        //language=SQL
        String sql = """
                INSERT INTO recording_tags (recording_id, tag_key, tag_value)
                VALUES (:recording_id, :tag_key, :tag_value)""";

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("recording_id", recordingId)
                    .addValue("tag_key", entry.getKey())
                    .addValue("tag_value", entry.getValue());

            databaseClient.insert(StatementLabel.INSERT_RECORDING_TAG, sql, params);
        }
    }

    @Override
    public List<RecordingTag> listForRecording(String recordingId) {
        //language=sql
        String sql = "SELECT tag_key, tag_value FROM recording_tags WHERE recording_id = :recording_id";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recording_id", recordingId);

        return databaseClient.query(
                StatementLabel.FIND_RECORDING_TAGS, sql, params,
                (rs, _) -> new RecordingTag(rs.getString("tag_key"), rs.getString("tag_value")));
    }

    @Override
    public Map<String, List<RecordingTag>> listForRecordings(Collection<String> recordingIds) {
        if (recordingIds == null || recordingIds.isEmpty()) {
            return Map.of();
        }

        //language=sql
        String sql = """
                SELECT recording_id, tag_key, tag_value
                FROM recording_tags
                WHERE recording_id IN (:recording_ids)""";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recording_ids", recordingIds);

        List<TagRow> rows = databaseClient.query(
                StatementLabel.FIND_RECORDING_TAGS_FOR_MANY, sql, params,
                (rs, _) -> new TagRow(
                        rs.getString("recording_id"),
                        new RecordingTag(rs.getString("tag_key"), rs.getString("tag_value"))));

        return rows.stream()
                .collect(Collectors.groupingBy(
                        TagRow::recordingId,
                        Collectors.mapping(TagRow::tag, Collectors.toList())));
    }

    private record TagRow(String recordingId, RecordingTag tag) {
    }

    @Override
    public void deleteForRecording(String recordingId) {
        //language=sql
        String sql = "DELETE FROM recording_tags WHERE recording_id = :recording_id";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recording_id", recordingId);

        databaseClient.delete(StatementLabel.DELETE_RECORDING_TAGS, sql, params);
    }
}
