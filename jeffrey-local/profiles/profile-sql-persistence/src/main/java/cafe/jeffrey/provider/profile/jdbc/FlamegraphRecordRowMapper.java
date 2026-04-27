package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import org.springframework.jdbc.core.RowMapper;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.jfrparser.db.type.DbJfrMethod;
import cafe.jeffrey.jfrparser.db.type.DbJfrStackTrace;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;

import java.sql.ResultSet;
import java.sql.SQLException;

public record FlamegraphRecordRowMapper(Type eventType) implements RowMapper<FlamegraphRecord> {

    @Override
    public FlamegraphRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        DbJfrStackTrace stacktrace = new DbJfrStackTrace(
                rs.getLong("stacktrace_hash"), FlamegraphMapperUtils.getStackFrames(rs));

        return new FlamegraphRecord(
                eventType,
                stacktrace,
                null,
                DbJfrMethod.ofClass(rs.getString("weight_entity")),
                rs.getLong("total_samples"),
                rs.getLong("total_weight")
        );
    }
}
