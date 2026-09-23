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

package cafe.jeffrey.jfrparser.jdk;

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
