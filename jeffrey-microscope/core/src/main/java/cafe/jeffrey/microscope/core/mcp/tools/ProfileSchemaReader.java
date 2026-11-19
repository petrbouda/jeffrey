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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@link ProfileSchema} from one profile's database, through the same catalogue reads the
 * {@code jfr_listTables}, {@code jfr_describeTable} and {@code jfr_listEventTypes} tools render.
 */
public final class ProfileSchemaReader {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileSchemaReader.class);

    /** The Microscope page that lists what the schema's event types list. */
    static final MicroscopeView EVENT_TYPES_VIEW = MicroscopeView.EVENT_TYPES;

    private final DataSource dataSource;

    public ProfileSchemaReader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Every table and view with its columns, and every event type with its count. Reads only the
     * catalogue and one grouped count over {@code events}; it writes and computes nothing.
     * <p>
     * Builds the page link from the request being served, so it is called while one is bound.
     */
    public ProfileSchema read(String profileId) {
        try (Connection conn = dataSource.getConnection()) {
            List<ProfileSchema.Relation> relations = new ArrayList<>();
            for (JfrDatabaseCatalog.Relation relation : JfrDatabaseCatalog.relations(conn)) {
                relations.add(new ProfileSchema.Relation(
                        relation.name(),
                        relation.view(),
                        JfrDatabaseCatalog.columns(conn, relation.name()),
                        JfrDatabaseCatalog.note(relation.name()).orElse(null)));
            }
            return new ProfileSchema(
                    profileId,
                    List.copyOf(relations),
                    JfrDatabaseCatalog.eventTypes(conn),
                    UiLinks.view(profileId, EVENT_TYPES_VIEW));
        } catch (SQLException e) {
            LOG.error("Failed to read the profile schema: profile_id={} message={}", profileId, e.getMessage(), e);
            throw new ToolExecutionException("Failed to read the profile schema: " + e.getMessage(), e);
        }
    }
}
