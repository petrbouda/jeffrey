package pbouda.jeffrey.provider.profile.query;

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.jfrparser.db.type.DbJfrMethod;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackTrace;
import pbouda.jeffrey.provider.profile.model.FlamegraphRecord;

import java.sql.ResultSet;
import java.sql.SQLException;

public record FlamegraphRecordWithThreadsRowMapper(Type eventType) implements RowMapper<FlamegraphRecord> {

    @Override
    public FlamegraphRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        DbJfrStackTrace stacktrace = new DbJfrStackTrace(
                rs.getLong("stacktrace_hash"), FlamegraphMapperUtils.getStackFrames(rs));

        return new FlamegraphRecord(
                eventType,
                stacktrace,
                FlamegraphMapperUtils.getThread(rs),
                DbJfrMethod.ofClass(rs.getString("weight_entity")),
                rs.getLong("total_samples"),
                rs.getLong("total_weight")
        );
    }
}
