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

public class SimpleCondition implements Condition {
    
    private final String column;
    private final String operator;
    private final ValueType value;
    
    public SimpleCondition(String column, String operator, ValueType value) {
        this.column = column;
        this.operator = operator;
        this.value = value;
    }
    
    @Override
    public String toSql() {
        return column + " " + operator + " " + value.format();
    }
}
