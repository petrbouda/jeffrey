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

public interface RecordingFileIterator<PARTIAL, RESULT> {

    /**
     * Iterates over the recording file, processes the particular events, gets PARTIAL entities
     * (intermediate results that can be merged and transformed) and collects them into a RESULT entity.
     * Useful for parallel processing of multiple recordings, retrieving intermediate result,
     * and merging them into a single entity.
     *
     * @param collector collector that merges the PARTIAL entities into a single RESULT entity.
     * @return merged RESULT entity by the collector.
     */
    RESULT collect(Collector<PARTIAL, RESULT> collector);

    /**
     * Iterates over the recording file, processes the particular events, gets PARTIAL entities
     * and returns them in combined form.
     *
     * @param collector collector that combines the PARTIAL entities into a single PARTIAL entity.
     * @return combined PARTIAL entities.
     */
    PARTIAL partialCollect(Collector<PARTIAL, ?> collector);
}
