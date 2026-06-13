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

import java.util.List;

/**
 * Bytecode-instrumentation activity for a profile: the retransformation batches and the individual
 * class redefinitions they produced.
 *
 * @param redefinitions individual {@code jdk.ClassRedefinition} entries (one per redefined class)
 * @param retransforms  {@code jdk.RetransformClasses} batches (one per agent retransformation call)
 */
public record RedefinitionData(
        List<ClassRedefinitionStat> redefinitions,
        List<RetransformBatch> retransforms) {
}
