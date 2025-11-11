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

package pbouda.jeffrey.provider.writer.clickhouse.writer;

import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.provider.api.model.writer.EnhancedEventType;
import pbouda.jeffrey.provider.writer.clickhouse.ClickHouseClient;
import pbouda.jeffrey.provider.writer.clickhouse.model.ClickHouseEventType;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;

public class ClickHouseEventTypeWriter extends ClickHouseBatchingWriter<EnhancedEventType, ClickHouseEventType> {

    private final String profileId;

    public ClickHouseEventTypeWriter(ClickHouseClient clickHouseClient, String profileId, int batchSize) {
        super("event_types", clickHouseClient, batchSize, StatementLabel.INSERT_EVENT_TYPES);
        this.profileId = profileId;
    }

    @Override
    protected ClickHouseEventType entityMapper(EnhancedEventType entity) {
        return new ClickHouseEventType(
                profileId,
                entity.eventType().name(),
                entity.eventType().label(),
                entity.eventType().typeId(),
                entity.eventType().description(),
                Json.toString(entity.eventType().categories()),
                entity.source().getId(),
                entity.subtype(),
                entity.containsStackTraces(),
                Json.toJson(entity.extras()),
                Json.toJson(entity.settings()),
                entity.eventType().columns().toString()
        );
    }
}
