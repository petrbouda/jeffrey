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

package cafe.jeffrey.provider.profile.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.List;

public class JdbcProfileFrameRepository implements ProfileFrameRepository {

    //language=SQL
    private static final String COUNT_BY_CLASS_NAME = """
            SELECT COUNT(*) FROM frames WHERE class_name LIKE '%' || :search || '%'""";

    //language=SQL
    private static final String PREVIEW_RENAME = """
            SELECT class_name, REPLACE(class_name, :search, :replacement) AS renamed_class_name, method_name
            FROM frames
            WHERE class_name LIKE '%' || :search || '%'
            LIMIT :limit""";

    //language=SQL
    private static final String RENAME_CLASS_NAMES = """
            UPDATE frames
            SET class_name = REPLACE(class_name, :search, :replacement)
            WHERE class_name LIKE '%' || :search || '%'""";

    private final DatabaseClient databaseClient;

    public JdbcProfileFrameRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROFILE_FRAMES);
    }

    @Override
    public int countFramesByClassNameContaining(String search) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("search", search);

        return (int) databaseClient.queryLong(StatementLabel.COUNT_FRAMES_BY_CLASS_NAME, COUNT_BY_CLASS_NAME, params);
    }

    @Override
    public List<FrameRenamePreview> previewRename(String search, String replacement, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("search", search)
                .addValue("replacement", replacement)
                .addValue("limit", limit);

        return databaseClient.query(StatementLabel.PREVIEW_RENAME_FRAMES, PREVIEW_RENAME, params,
                (rs, _) -> new FrameRenamePreview(
                        rs.getString("class_name"),
                        rs.getString("renamed_class_name"),
                        rs.getString("method_name")));
    }

    @Override
    public int renameClassNames(String search, String replacement) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("search", search)
                .addValue("replacement", replacement);

        return databaseClient.update(StatementLabel.RENAME_FRAME_CLASS_NAMES, RENAME_CLASS_NAMES, params);
    }
}
