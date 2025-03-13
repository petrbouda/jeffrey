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

package pbouda.jeffrey.provider.writer.sqlite.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.jfrparser.api.record.GenericRecord;
import pbouda.jeffrey.provider.api.streamer.EventStreamConfigurer;
import pbouda.jeffrey.provider.api.streamer.EventStreamer;
import pbouda.jeffrey.provider.api.streamer.EventStreamerFactory;
import pbouda.jeffrey.provider.api.streamer.model.FlamegraphRecord;
import pbouda.jeffrey.provider.api.streamer.model.TimeseriesRecord;

import java.util.List;

public class JdbcEventStreamerFactory implements EventStreamerFactory {

    private final JdbcTemplate jdbcTemplate;
    private final String profileId;

    public JdbcEventStreamerFactory(JdbcTemplate jdbcTemplate, String profileId) {
        this.jdbcTemplate = jdbcTemplate;
        this.profileId = profileId;
    }

    @Override
    public EventStreamer<TimeseriesRecord> newTimeseriesStreamer(EventStreamConfigurer configurer) {
        RowMapper<TimeseriesRecord> mapper = (r, n) ->
                TimeseriesRecord.secondsAndValues(r.getLong("seconds"), r.getLong("value"));

        String valueField = configurer.useWeight()
                ? "sum(events.weight) as value"
                : "sum(events.samples) as value";

        List<String> baseFields = List.of("(events.timestamp_from_start / 1000) AS seconds", valueField);

        QueryBuilder queryBuilder = new QueryBuilder(profileId, configurer, baseFields)
                .addGroupBy("seconds")
                .addOrderBy("seconds");

        return new JdbcEventStreamer<>(jdbcTemplate, mapper, queryBuilder);
    }

    @Override
    public EventStreamer<FlamegraphRecord> newFlamegraphStreamer(EventStreamConfigurer configurer) {
        List<String> baseFields = List.of(
                "sum(events.samples) AS samples",
                "sum(events.weight) as weight",
                "events.weight_entity");

        // Always include stackframes (otherwise flamegraph cannot be generated)
        configurer.withIncludeFrames();

        QueryBuilder queryBuilder = new QueryBuilder(profileId, configurer, baseFields)
                .addGroupBy("events.stacktrace_id");

        return new JdbcEventStreamer<>(jdbcTemplate, new FlamegraphRecordRowMapper(configurer), queryBuilder);
    }

    @Override
    public EventStreamer<GenericRecord> newGenericStreamer(EventStreamConfigurer configurer) {
        return new JdbcEventStreamer<>(
                jdbcTemplate,
                new GenericRecordRowMapper(configurer),
                new QueryBuilder(profileId, configurer));
    }
}
