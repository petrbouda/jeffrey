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
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.streamer.EventStreamer;
import pbouda.jeffrey.provider.api.streamer.EventStreamerFactory;
import pbouda.jeffrey.provider.api.streamer.model.FlamegraphRecord;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.provider.api.streamer.model.SubSecondRecord;
import pbouda.jeffrey.provider.api.streamer.model.TimeseriesRecord;

import java.util.List;

public class JdbcEventStreamerFactory implements EventStreamerFactory {

    private static final RowMapper<TimeseriesRecord> SIMPLE_TIMESERIES_RECORD_MAPPER =
            (r, n) -> TimeseriesRecord.secondsAndValues(r.getLong("seconds"), r.getLong("value"));

    private final JdbcTemplate jdbcTemplate;
    private final String profileId;

    public JdbcEventStreamerFactory(JdbcTemplate jdbcTemplate, String profileId) {
        this.jdbcTemplate = jdbcTemplate;
        this.profileId = profileId;
    }

    @Override
    public EventStreamer<SubSecondRecord> newSubSecondStreamer(EventQueryConfigurer configurer) {
        RowMapper<SubSecondRecord> mapper = (r, n) ->
                new SubSecondRecord(r.getLong("timestamp_from_start"), r.getLong("value"));

        String valueField = configurer.useWeight()
                ? "events.weight as value"
                : "events.samples as value";

        List<String> baseFields = List.of("events.timestamp_from_start", valueField);
        GenericQueryBuilder queryBuilder = new GenericQueryBuilder(profileId, configurer, baseFields);

        return new JdbcEventStreamer<>(jdbcTemplate, mapper, queryBuilder);
    }

    @Override
    public EventStreamer<TimeseriesRecord> newTimeseriesStreamer(EventQueryConfigurer configurer) {
        QueryBuilder queryBuilder = new TimeseriesQueryBuilder(configurer.includeFrames())
                .withProfileId(profileId)
                .withEventType(configurer.eventTypes().getFirst())
                .withWeight(configurer.useWeight())
                .withTimeRange(configurer.timeRange())
                .filterStacktraceTypes(configurer.filterStacktraceTypes())
                .filterStacktraceTags(configurer.filterStacktraceTags());

        RowMapper<TimeseriesRecord> mapper = configurer.includeFrames()
                ? new TimeseriesRecordRowMapper()
                : SIMPLE_TIMESERIES_RECORD_MAPPER;

        return new JdbcEventStreamer<>(jdbcTemplate, mapper, queryBuilder);
    }

    @Override
    public EventStreamer<FlamegraphRecord> newFlamegraphStreamer(EventQueryConfigurer configurer) {
        List<String> baseFields = List.of(
                "sum(events.samples) AS samples",
                "sum(events.weight) as weight",
                "events.weight_entity");

        // Always include stackframes (otherwise flamegraph cannot be generated)
        configurer.withIncludeFrames();

        GenericQueryBuilder queryBuilder = new GenericQueryBuilder(profileId, configurer, baseFields)
                .addGroupBy("events.stacktrace_id");

        return new JdbcEventStreamer<>(jdbcTemplate, new FlamegraphRecordRowMapper(configurer), queryBuilder);
    }

    @Override
    public EventStreamer<GenericRecord> newGenericStreamer(EventQueryConfigurer configurer) {
        return new JdbcEventStreamer<>(
                jdbcTemplate,
                new GenericRecordRowMapper(configurer),
                new GenericQueryBuilder(profileId, configurer));
    }
}
