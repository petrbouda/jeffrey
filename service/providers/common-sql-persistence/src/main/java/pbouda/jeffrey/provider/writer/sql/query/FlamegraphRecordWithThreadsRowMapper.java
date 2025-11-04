package pbouda.jeffrey.provider.writer.sql.query;

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.db.type.DbJfrMethod;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackTrace;
import pbouda.jeffrey.provider.api.repository.model.FlamegraphRecord;

import java.sql.ResultSet;
import java.sql.SQLException;

public record FlamegraphRecordWithThreadsRowMapper(
        Type eventType, boolean useWeight) implements RowMapper<FlamegraphRecord> {

    @Override
    public FlamegraphRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        DbJfrStackTrace stacktrace = new DbJfrStackTrace(
                rs.getLong("stacktrace_hash"), FlamegraphMapperUtils.getStackFrames(rs));

        JfrClass weightEntity = null;
        if (useWeight) {
            weightEntity = DbJfrMethod.ofClass(rs.getString("weight_entity"));
        }

        return new FlamegraphRecord(
                eventType,
                stacktrace,
                FlamegraphMapperUtils.getThread(rs),
                weightEntity,
                rs.getLong("total_samples"),
                rs.getLong("total_weight")
        );
    }
}
