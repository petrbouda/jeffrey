/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core.manager.ide;

/**
 * Request to open a source location in the developer's IDE.
 *
 * @param fqn    fully-qualified class name (e.g. {@code com.example.OrderService})
 * @param method method reference as {@code ClassName.methodName} (e.g. {@code OrderService.processOrder})
 * @param line   source line number, or {@code -1} when unknown
 */
public record IdeOpenRequest(String fqn, String method, int line) {

    public IdeOpenRequest {
        if (fqn == null || fqn.isBlank()) {
            throw new IllegalArgumentException("fqn must not be blank");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method must not be blank");
        }
    }
}
