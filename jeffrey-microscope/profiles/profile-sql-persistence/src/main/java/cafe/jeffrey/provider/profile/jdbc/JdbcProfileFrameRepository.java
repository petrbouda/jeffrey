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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

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
    private final FramesCacheSlot framesCacheSlot;

    public JdbcProfileFrameRepository(DatabaseClientProvider databaseClientProvider, FramesCacheSlot framesCacheSlot) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROFILE_FRAMES);
        this.framesCacheSlot = framesCacheSlot;
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

        int renamed = databaseClient.update(StatementLabel.RENAME_FRAME_CLASS_NAMES, RENAME_CLASS_NAMES, params);
        // The cached frames hold the old class names — the next request must reload them
        framesCacheSlot.invalidate();
        return renamed;
    }
}
