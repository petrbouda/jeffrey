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
