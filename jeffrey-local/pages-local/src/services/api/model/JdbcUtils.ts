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