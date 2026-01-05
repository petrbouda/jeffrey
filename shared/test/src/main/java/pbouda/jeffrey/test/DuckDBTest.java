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

package pbouda.jeffrey.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to configure DuckDB integration tests.
 * <p>
 * When applied to a test class, creates a fresh in-memory DuckDB connection for each test method
 * and optionally runs Flyway migrations to set up the schema.
 * <p>
 * Example usage:
 * <pre>{@code
 * @DuckDBTest(migration = "classpath:db/migration/platform")
 * class MyRepositoryTest {
 *     @Test
 *     void testMethod(DataSource dataSource) {
 *         // dataSource is injected automatically
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DuckDBExtension.class)
public @interface DuckDBTest {

    /**
     * Flyway migration location to run before each test.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code "classpath:db/migration/platform"} - Platform schema</li>
     *   <li>{@code "classpath:db/migration/profile"} - Profile schema</li>
     * </ul>
     *
     * @return the migration location, or empty string to skip migrations
     */
    String migration() default "";
}
