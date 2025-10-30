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

import pbouda.jeffrey.provider.api.model.EventFrame;
import pbouda.jeffrey.provider.api.model.writer.EventFrameWithHash;
import pbouda.jeffrey.provider.writer.clickhouse.ClickHouseClient;
import pbouda.jeffrey.provider.writer.clickhouse.model.ClickHouseFrame;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;

public class ClickHouseFrameWriter extends ClickHouseBatchingWriter<EventFrameWithHash, ClickHouseFrame> {

    public ClickHouseFrameWriter(ClickHouseClient clickHouseClient, String profileId, int batchSize) {
        super("frames", clickHouseClient, batchSize, StatementLabel.INSERT_FRAMES);
    }

    @Override
    protected ClickHouseFrame entityMapper(EventFrameWithHash entity) {
        EventFrame frame = entity.frame();
        return new ClickHouseFrame(
                entity.hash(),
                frame.clazz(),
                frame.method(),
                frame.type(),
                frame.line(),
                frame.bci()
        );
    }
}
