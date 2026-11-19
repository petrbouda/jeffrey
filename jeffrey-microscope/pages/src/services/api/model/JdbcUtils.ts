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

export default class JdbcUtils {
  /**
   * Cleans JDBC operation name by removing the "JDBC " prefix and " Statement" suffix
   *
   * Examples:
   * - "JDBC Query Statement" -> "Query"
   * - "JDBC Insert Statement" -> "Insert"
   * - "JDBC Generic Execute Statement" -> "Generic Execute"
   *
   * @param operation The raw JDBC operation name
   * @returns The cleaned operation name
   */
  public static cleanOperationName(operation: string): string {
    return operation.replace(/^JDBC\s+/, '').replace(/\s+Statement$/, '');
  }
}
