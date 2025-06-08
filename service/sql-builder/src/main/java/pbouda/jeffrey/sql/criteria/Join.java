/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

public class Join {

    private final JoinType type;
    private final String table;
    private final String stringCondition;
    private final Condition conditionObject;

    // Constructor for string-based condition (backward compatibility)
    public Join(JoinType type, String table, String condition) {
        this.type = type;
        this.table = table;
        this.stringCondition = condition;
        this.conditionObject = null;
    }

    // Constructor for Condition object
    public Join(JoinType type, String table, Condition condition) {
        this.type = type;
        this.table = table;
        this.stringCondition = null;
        this.conditionObject = condition;
    }

    public String toSql() {
        String condition;
        if (conditionObject != null) {
            condition = conditionObject.toSql();
        } else {
            condition = stringCondition;
        }
        return type.getSql() + " " + table + " ON " + condition;
    }
}
