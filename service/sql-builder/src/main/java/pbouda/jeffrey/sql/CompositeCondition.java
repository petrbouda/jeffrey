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

package pbouda.jeffrey.sql;

import java.util.StringJoiner;

public class CompositeCondition implements Condition {
    
    private final LogicalOperator operator;
    private final Condition[] conditions;
    
    public CompositeCondition(LogicalOperator operator, Condition... conditions) {
        this.operator = operator;
        this.conditions = conditions;
    }
    
    @Override
    public String toSql() {
        if (conditions.length == 0) {
            return "";
        }
        
        if (conditions.length == 1) {
            return conditions[0].toSql();
        }
        
        StringJoiner joiner = new StringJoiner(" " + operator.getSql() + " ");
        for (Condition condition : conditions) {
            joiner.add(condition.toSql());
        }
        
        // Add parentheses for all composite conditions
        return "(" + joiner + ")";
    }
}
