/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.sql;

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
