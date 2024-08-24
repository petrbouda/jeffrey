/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.common;

import java.util.function.Supplier;

public interface Collector<PARTIAL, RESULT> {

    /**
     * Returns a partial result that should be used as the initial or empty value.
     *
     * @return empty partial result.
     */
    Supplier<PARTIAL> empty();

    /**
     * Combines two partial results into a single one and returns the one
     * that should be used as the result of the combination and push to
     * the next combination or finisher.
     *
     * @param partial1 the first partial result.
     * @param partial2 the second partial result.
     * @return the combined partial result.
     */
    PARTIAL combiner(PARTIAL partial1, PARTIAL partial2);

    /**
     * Transforms the combined partial results into the final entity/result.
     *
     * @param combined the combined partial results.
     * @return transformed combined partial results.
     */
    RESULT finisher(PARTIAL combined);
}
