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

package pbouda.jeffrey.sql.criteria;

import java.util.List;
import java.util.StringJoiner;

public class NotInOrNullCondition implements Condition {

    private final String column;
    private final List<? extends ValueType> values;

    public NotInOrNullCondition(String column, List<? extends ValueType> values) {
        this.column = column;
        this.values = values;
    }

    @Override
    public String toSql() {
        if (values.isEmpty()) {
            return column + " IS NULL";
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (ValueType value : values) {
            joiner.add(value.format());
        }

        return "(" + column + " NOT IN (" + joiner + ") OR " + column + " IS NULL)";
    }
}
