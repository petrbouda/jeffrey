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

package pbouda.jeffrey.provider.writer.sql.query.timeseries;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.shared.Json;
import pbouda.jeffrey.provider.api.repository.model.SecondValue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Predicate;

public class FilterableTimeseriesRecordRowMapper implements RowMapper<SecondValue> {

    private final Predicate<ObjectNode> filter;

    public FilterableTimeseriesRecordRowMapper(Predicate<ObjectNode> filter) {
        this.filter = filter;
    }

    @Override
    public SecondValue mapRow(ResultSet rs, int rn) throws SQLException {
        ObjectNode jsonFields = (ObjectNode) Json.readTree(rs.getString("event_fields"));
        if (!filter.test(jsonFields)) {
            // Skip this record if it doesn't match the filter
            return null;
        }
        return new SecondValue(rs.getLong("seconds"), rs.getLong("samples"));
    }
}
