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

package pbouda.jeffrey.provider.writer.sqlite.query.timeseries;

import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.writer.sqlite.query.SQLParts;
import pbouda.jeffrey.sql.SQLBuilder;

public class SimpleTimeseriesQueryBuilder extends AbstractTimeseriesQueryBuilder {

    public SimpleTimeseriesQueryBuilder(String profileId, Type eventType, boolean useWeight) {
        super(createBaseBuilder(profileId, eventType, useWeight), true);
    }

    private static SQLBuilder createBaseBuilder(String profileId, Type eventType, boolean useWeight) {
        return new SQLBuilder()
                .addColumn("(events.start_timestamp_from_beginning / 1000) AS seconds")
                .addColumn("sum(" + (useWeight ? "events.weight" : "events.samples") + ") as value")
                .from("events")
                .where(SQLParts.profileAndType(profileId, eventType))
                .groupBy("seconds")
                .orderBy("seconds");
    }
}
