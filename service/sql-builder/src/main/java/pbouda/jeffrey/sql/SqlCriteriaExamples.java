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

import static pbouda.jeffrey.sql.SQLBuilder.*;

public class SqlCriteriaExamples {

    public static void main(String[] args) {

        // Example 1: Basic SELECT with WHERE
        String query1 = select("id", "name", "email")
                .from("users")
                .where("age", ">=", l(18))
                .and("status", "=", l("active"))
                .build();

        System.out.println("Query 1:");
        System.out.println(query1);
        System.out.println();

        // Example 2: Complex query with JOINs and conditions
        String query2 = select("u.name", "p.title", "c.name as category")
                .from("users", "u")
                .join("posts p", "u.id = p.user_id")
                .leftJoin("categories c", "p.category_id = c.id")
                .where("u.created_at", ">", l("2023-01-01"))
                .and(or(
                        eq("p.status", l("published")),
                        eq("p.status", l("featured"))
                ))
                .groupBy("u.id", "p.id")
                .having("COUNT(p.id)", ">", l(5))
                .orderBy("u.name", "ASC")
                .build();

        System.out.println("Query 2:");
        System.out.println(query2);
        System.out.println();

        // Example 3: Using IN condition
        String query3 = select("*")
                .from("products")
                .where(inInts("category_id", 1, 2, 3, 5))
                .and("price", "BETWEEN", l("10 AND 100"))
                .orderBy("price", "DESC")
                .build();

        System.out.println("Query 3:");
        System.out.println(query3);
        System.out.println();

        // Example 4: Complex nested conditions
        String query4 = select("customer_id", "SUM(amount) as total")
                .from("orders")
                .where(and(
                        gte("order_date", l("2023-01-01")),
                        or(
                                eq("status", l("completed")),
                                eq("status", l("shipped"))
                        )
                ))
                .groupBy("customer_id")
                .having("SUM(amount)", ">", l(1000))
                .build();

        System.out.println("Query 4:");
        System.out.println(query4);
    }
}
