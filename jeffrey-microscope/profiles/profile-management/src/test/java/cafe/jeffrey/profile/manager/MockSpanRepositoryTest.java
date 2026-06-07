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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.span.SpanTagStat;
import cafe.jeffrey.provider.profile.api.SpanRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockSpanRepositoryTest {

    private final MockSpanRepository repository = new MockSpanRepository();

    @Test
    void generatesNonEmptyMultiTagData() {
        List<SpanRecord> spans = repository.listSpans();

        assertFalse(spans.isEmpty());
        Set<String> tags = spans.stream().map(SpanRecord::tag).collect(Collectors.toSet());
        assertTrue(tags.size() >= 3, "expected several distinct tags, got " + tags);
    }

    @Test
    void feedsTheTagStatisticsView() {
        // The mock must always give the table/heatmap views something to render.
        List<SpanTagStat> stats = new SpanManagerImpl(repository).tagStatistics();
        assertFalse(stats.isEmpty());
    }
}
