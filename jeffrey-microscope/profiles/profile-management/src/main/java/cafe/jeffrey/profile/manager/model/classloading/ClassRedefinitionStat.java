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

package cafe.jeffrey.profile.manager.model.classloading;

/**
 * A single class redefinition, derived from a {@code jdk.ClassRedefinition} event. Redefinitions are
 * driven by bytecode-instrumentation agents (JFR retransformation, APM agents, mocking frameworks).
 *
 * @param className         binary name of the redefined class
 * @param modificationCount number of times the class has been changed
 * @param redefinitionId    identifier of the redefinition batch this class belongs to
 */
public record ClassRedefinitionStat(
        String className,
        int modificationCount,
        long redefinitionId) {
}
