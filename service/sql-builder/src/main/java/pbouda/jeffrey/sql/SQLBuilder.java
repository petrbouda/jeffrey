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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SQLBuilder {

    private final List<String> selectColumns = new ArrayList<>();
    private final List<String> fromTables = new ArrayList<>();
    private final List<Join> joins = new ArrayList<>();
    private final List<Condition> whereConditions = new ArrayList<>();
    private final List<String> groupByColumns = new ArrayList<>();
    private final List<Condition> havingConditions = new ArrayList<>();
    private final List<String> orderByColumns = new ArrayList<>();

    public static SQLBuilder select(String... columns) {
        SQLBuilder criteria = new SQLBuilder();
        Collections.addAll(criteria.selectColumns, columns);
        return criteria;
    }

    public SQLBuilder from(String table) {
        this.fromTables.add(table);
        return this;
    }

    public SQLBuilder from(String table, String alias) {
        this.fromTables.add(table + " " + alias);
        return this;
    }

    public SQLBuilder join(String table, String condition) {
        this.joins.add(new Join(JoinType.INNER, table, condition));
        return this;
    }

    public SQLBuilder join(String table, Condition condition) {
        this.joins.add(new Join(JoinType.INNER, table, condition));
        return this;
    }

    public SQLBuilder leftJoin(String table, String condition) {
        this.joins.add(new Join(JoinType.LEFT, table, condition));
        return this;
    }

    public SQLBuilder leftJoin(String table, Condition condition) {
        this.joins.add(new Join(JoinType.LEFT, table, condition));
        return this;
    }

    public SQLBuilder rightJoin(String table, String condition) {
        this.joins.add(new Join(JoinType.RIGHT, table, condition));
        return this;
    }

    public SQLBuilder rightJoin(String table, Condition condition) {
        this.joins.add(new Join(JoinType.RIGHT, table, condition));
        return this;
    }

    public SQLBuilder where(String column, String operator, ValueType value) {
        this.whereConditions.add(new SimpleCondition(column, operator, value));
        return this;
    }

    public SQLBuilder where(Condition condition) {
        this.whereConditions.add(condition);
        return this;
    }

    public SQLBuilder and(String column, String operator, ValueType value) {
        this.whereConditions.add(new LogicalCondition(LogicalOperator.AND,
                new SimpleCondition(column, operator, value)));
        return this;
    }

    public SQLBuilder and(Condition condition) {
        this.whereConditions.add(new LogicalCondition(LogicalOperator.AND, condition));
        return this;
    }

    public SQLBuilder or(String column, String operator, ValueType value) {
        this.whereConditions.add(new LogicalCondition(LogicalOperator.OR,
                new SimpleCondition(column, operator, value)));
        return this;
    }

    public SQLBuilder or(Condition condition) {
        this.whereConditions.add(new LogicalCondition(LogicalOperator.OR, condition));
        return this;
    }

    public SQLBuilder groupBy(String... columns) {
        Collections.addAll(this.groupByColumns, columns);
        return this;
    }

    public SQLBuilder having(String column, String operator, ValueType value) {
        this.havingConditions.add(new SimpleCondition(column, operator, value));
        return this;
    }

    public SQLBuilder having(Condition condition) {
        this.havingConditions.add(condition);
        return this;
    }

    public SQLBuilder orderBy(String column) {
        this.orderByColumns.add(column);
        return this;
    }

    public SQLBuilder orderBy(String column, String direction) {
        this.orderByColumns.add(column + " " + direction);
        return this;
    }

    /**
     * Adds a single column to the SELECT clause.
     *
     * @param column the column to add
     * @return this SqlCriteria instance for method chaining
     * @throws IllegalArgumentException if column is null or empty
     */
    public SQLBuilder addColumn(String column) {
        if (column == null || column.trim().isEmpty()) {
            throw new IllegalArgumentException("Column cannot be null or empty");
        }
        this.selectColumns.add(column.trim());
        return this;
    }

    /**
     * Adds multiple columns to the SELECT clause.
     *
     * @param columns the columns to add
     * @return this SqlCriteria instance for method chaining
     * @throws IllegalArgumentException if columns array is null or contains null/empty values
     */
    public SQLBuilder addColumns(String... columns) {
        if (columns == null) {
            throw new IllegalArgumentException("Columns array cannot be null");
        }
        for (String column : columns) {
            addColumn(column);
        }
        return this;
    }

    /**
     * Adds multiple columns from a list to the SELECT clause.
     *
     * @param columns the list of columns to add
     * @return this SqlCriteria instance for method chaining
     * @throws IllegalArgumentException if columns list is null or contains null/empty values
     */
    public SQLBuilder addColumns(List<String> columns) {
        if (columns == null) {
            throw new IllegalArgumentException("Columns list cannot be null");
        }
        for (String column : columns) {
            addColumn(column);
        }
        return this;
    }

    /**
     * Clears all existing SELECT columns.
     *
     * @return this SqlCriteria instance for method chaining
     */
    public SQLBuilder clearColumns() {
        this.selectColumns.clear();
        return this;
    }

    /**
     * Gets a copy of the current SELECT columns.
     *
     * @return a new list containing all current SELECT columns
     */
    public List<String> getColumns() {
        return new ArrayList<>(this.selectColumns);
    }

    /**
     * Checks if the criteria has any SELECT columns defined.
     *
     * @return true if no columns are defined, false otherwise
     */
    public boolean hasColumns() {
        return !this.selectColumns.isEmpty();
    }

    public String build() {
        StringBuilder sql = new StringBuilder();

        // SELECT clause
        sql.append("SELECT ");
        if (selectColumns.isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", selectColumns));
        }

        // FROM clause
        if (!fromTables.isEmpty()) {
            sql.append(" FROM ");
            sql.append(String.join(", ", fromTables));
        }

        // JOIN clauses
        for (Join join : joins) {
            sql.append(" ").append(join.toSql());
        }

        // WHERE clause
        if (!whereConditions.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(buildConditionsString(whereConditions));
        }

        // GROUP BY clause
        if (!groupByColumns.isEmpty()) {
            sql.append(" GROUP BY ");
            sql.append(String.join(", ", groupByColumns));
        }

        // HAVING clause
        if (!havingConditions.isEmpty()) {
            sql.append(" HAVING ");
            sql.append(buildConditionsString(havingConditions));
        }

        // ORDER BY clause
        if (!orderByColumns.isEmpty()) {
            sql.append(" ORDER BY ");
            sql.append(String.join(", ", orderByColumns));
        }

        return sql.toString();
    }

    private String buildConditionsString(List<Condition> conditions) {
        if (conditions.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Condition condition : conditions) {
            if (!first) {
                result.append(" ");
            }
            result.append(condition.toSql());
            first = false;
        }

        return result.toString();
    }

    // Static factory methods for conditions
    public static Condition eq(String column, ValueType value) {
        return new SimpleCondition(column, "=", value);
    }

    public static Condition ne(String column, ValueType value) {
        return new SimpleCondition(column, "!=", value);
    }

    public static Condition gt(String column, ValueType value) {
        return new SimpleCondition(column, ">", value);
    }

    public static Condition gte(String column, ValueType value) {
        return new SimpleCondition(column, ">=", value);
    }

    public static Condition lt(String column, ValueType value) {
        return new SimpleCondition(column, "<", value);
    }

    public static Condition lte(String column, ValueType value) {
        return new SimpleCondition(column, "<=", value);
    }

    public static Condition like(String column, String pattern) {
        return new SimpleCondition(column, "LIKE", l(pattern));
    }

    public static Condition inInts(String column, int... values) {
        return new InCondition(column, Arrays.stream(values).mapToObj(LongLiteral::new).toList());
    }

    public static Condition inInts(String column, List<Integer> values) {
        return new InCondition(column, values.stream().map(LongLiteral::new).toList());
    }

    public static Condition in(String column, List<String> values) {
        return new InCondition(column, values.stream().map(StringLiteral::new).toList());
    }

    public static Condition in(String column, String... values) {
        return new InCondition(column, Arrays.stream(values).map(StringLiteral::new).toList());
    }

    public static Condition and(Condition... conditions) {
        return new CompositeCondition(LogicalOperator.AND, conditions);
    }

    public static Condition or(Condition... conditions) {
        return new CompositeCondition(LogicalOperator.OR, conditions);
    }

    public static Condition notInOrNullInts(String column, List<Integer> values) {
        return new NotInOrNullCondition(column, values.stream().map(LongLiteral::new).toList());
    }

    public static Condition notInOrNull(String column, List<String> values) {
        return new NotInOrNullCondition(column, values.stream().map(StringLiteral::new).toList());
    }

    public static Condition notInOrNull(String column, String... values) {
        return new NotInOrNullCondition(column, Arrays.stream(values).map(StringLiteral::new).toList());
    }

    public static ValueType l(Object value) {
        return switch (value) {
            case String str -> new StringLiteral(str);
            case Integer i -> new LongLiteral(i);
            case Long l -> new LongLiteral(l);
            case Boolean b -> new BooleanLiteral(b);
            default -> throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
        };
    }

    public static Column c(String name) {
        return new Column(name);
    }

    /**
     * Merges another SqlCriteria instance into this one.
     * This combines all components from the other criteria into this criteria.
     *
     * @param other the SqlCriteria to merge into this one
     * @return this SqlCriteria instance for method chaining
     * @throws IllegalArgumentException if other is null
     */
    public SQLBuilder merge(SQLBuilder other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot merge null SqlCriteria");
        }

        // Merge select columns (avoid duplicates)
        for (String column : other.selectColumns) {
            if (!this.selectColumns.contains(column)) {
                this.selectColumns.add(column);
            }
        }

        // Merge from tables
        this.fromTables.addAll(other.fromTables);

        // Merge joins
        this.joins.addAll(other.joins);

        // Merge where conditions
        if (!other.whereConditions.isEmpty()) {
            if (!this.whereConditions.isEmpty()) {
                // If both criteria have conditions, need to properly group the merged conditions
                if (other.whereConditions.size() == 1) {
                    // Single condition from other - add with AND prefix
                    Condition condition = other.whereConditions.get(0);
                    if (condition instanceof LogicalCondition) {
                        this.whereConditions.add(condition);
                    } else {
                        this.whereConditions.add(new LogicalCondition(LogicalOperator.AND, condition));
                    }
                } else {
                    // Multiple conditions from other - we need to carefully handle the logical structure
                    // First condition should not have a logical operator prefix when wrapped
                    Condition firstCondition = other.whereConditions.get(0);
                    StringBuilder conditionsString = new StringBuilder();
                    
                    // Handle first condition without logical operator
                    if (firstCondition instanceof LogicalCondition logicalCondition) {
                        // Strip the logical operator from the first condition
                        conditionsString.append(logicalCondition.getCondition().toSql());
                    } else {
                        conditionsString.append(firstCondition.toSql());
                    }
                    
                    // Add remaining conditions with their logical operators
                    for (int i = 1; i < other.whereConditions.size(); i++) {
                        conditionsString.append(" ").append(other.whereConditions.get(i).toSql());
                    }
                    
                    // Create a composite condition that preserves the existing logical structure
                    Condition wrappedCondition = new Condition() {
                        @Override
                        public String toSql() {
                            return "(" + conditionsString + ")";
                        }
                    };
                    this.whereConditions.add(new LogicalCondition(LogicalOperator.AND, wrappedCondition));
                }
            } else {
                // If this criteria has no conditions, just add all from other
                this.whereConditions.addAll(other.whereConditions);
            }
        }

        // Merge group by columns
        this.groupByColumns.addAll(other.groupByColumns);

        // Merge having conditions
        this.havingConditions.addAll(other.havingConditions);

        // Merge order by columns
        this.orderByColumns.addAll(other.orderByColumns);

        return this;
    }
}
