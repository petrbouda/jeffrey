/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Reading a nullable column without letting JDBC's primitive getters flatten the null away.
 * <p>
 * Every column this is used for has a null that means something the derivation deliberately
 * normalised to: a root span has no parent, a notification that carried no span has no span. Reading
 * those with {@code getLong} would turn them into {@code 0}, which is the very value the derivation
 * took out so that {@code IS NULL} could carry the meaning instead.
 */
final class JdbcNulls {

    private JdbcNulls() {
    }

    /** The column's value, or {@code null} when SQL said NULL rather than zero. */
    static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
