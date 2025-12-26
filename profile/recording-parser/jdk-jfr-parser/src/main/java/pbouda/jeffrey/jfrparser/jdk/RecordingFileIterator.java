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

package pbouda.jeffrey.jfrparser.jdk;

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
