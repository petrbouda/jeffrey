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
