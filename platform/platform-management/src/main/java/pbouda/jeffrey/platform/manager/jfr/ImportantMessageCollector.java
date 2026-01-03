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

package pbouda.jeffrey.platform.manager.jfr;

import pbouda.jeffrey.platform.manager.jfr.model.ImportantMessage;
import pbouda.jeffrey.repository.parser.Collector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Collector for combining ImportantMessage lists from multiple platformRepositories.
 * The final result is sorted by timestamp in descending order (newest first).
 */
public class ImportantMessageCollector implements Collector<List<ImportantMessage>, List<ImportantMessage>> {

    @Override
    public Supplier<List<ImportantMessage>> empty() {
        return ArrayList::new;
    }

    @Override
    public List<ImportantMessage> combiner(List<ImportantMessage> partial1, List<ImportantMessage> partial2) {
        List<ImportantMessage> combined = new ArrayList<>();
        combined.addAll(partial1);
        combined.addAll(partial2);
        return combined;
    }

    @Override
    public List<ImportantMessage> finisher(List<ImportantMessage> combined) {
        // Sort by createdAt descending (newest first)
        return combined.stream()
                .sorted(Comparator.comparing(ImportantMessage::createdAt).reversed())
                .toList();
    }
}
