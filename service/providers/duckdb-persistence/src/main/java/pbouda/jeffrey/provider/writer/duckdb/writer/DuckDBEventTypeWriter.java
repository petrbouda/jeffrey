/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.writer.duckdb.writer;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.provider.api.DataSourceUtils;
import pbouda.jeffrey.provider.api.model.EventType;
import pbouda.jeffrey.provider.api.model.writer.EnhancedEventType;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;

import javax.sql.DataSource;
import java.util.List;

import static pbouda.jeffrey.provider.writer.duckdb.writer.DuckDBAppenderUtils.nullableAppend;

public class DuckDBEventTypeWriter extends DuckDBBatchingWriter<EnhancedEventType> {

    private final String profileId;

    public DuckDBEventTypeWriter(DataSource dataSource, String profileId, int batchSize) {
        super("event_types", dataSource, batchSize, StatementLabel.INSERT_EVENT_TYPES);
        this.profileId = profileId;
    }

    @Override
    public void execute(DuckDBConnection connection, List<EnhancedEventType> batch) throws Exception {
        DuckDBConnection unwrapped = DataSourceUtils.unwrapConnection(connection, DuckDBConnection.class);
        try (DuckDBAppender appender = unwrapped.createAppender("event_types")) {
            for (EnhancedEventType entity : batch) {
                EventType eventType = entity.eventType();

                appender.beginRow();
                // profile_id - VARCHAR NOT NULL
                appender.append(profileId);
                // name - VARCHAR NOT NULL
                appender.append(eventType.name());
                // label - VARCHAR NOT NULL
                appender.append(eventType.label());
                // type_id - BIGINT (nullable)
                nullableAppend(appender, eventType.typeId());
                // description - VARCHAR (nullable)
                nullableAppend(appender, eventType.description());
                // categories - VARCHAR (nullable) - JSON array
                nullableAppend(appender, eventType.categories() != null ? Json.toString(eventType.categories()) : null);
                // source - VARCHAR NOT NULL
                appender.append(String.valueOf(entity.source().getId()));
                // subtype - VARCHAR (nullable)
                nullableAppend(appender, entity.subtype());
                // samples - BIGINT NOT NULL
                appender.append(entity.samples());
                // weight - BIGINT (nullable)
                nullableAppend(appender, entity.weight());
                // has_stacktrace - BOOLEAN NOT NULL
                appender.append(entity.containsStackTraces());
                // calculated - BOOLEAN NOT NULL
                appender.append(entity.calculated());
                // extras - VARCHAR (nullable) - JSON map
                nullableAppend(appender, entity.extras() != null ? Json.toString(entity.extras()) : null);
                // settings - VARCHAR (nullable) - JSON map
                nullableAppend(appender, entity.settings() != null ? Json.toString(entity.settings()) : null);
                // columns - VARCHAR (nullable) - JSON
                nullableAppend(appender, eventType.columns() != null ? eventType.columns().toString() : null);
                appender.endRow();
            }
        }
    }
}
