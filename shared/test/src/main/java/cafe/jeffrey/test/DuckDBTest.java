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

package cafe.jeffrey.test;

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
