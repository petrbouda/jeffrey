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

package pbouda.jeffrey.profile.ai.service;

import pbouda.jeffrey.profile.ai.model.HeapDumpContext;
import pbouda.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import pbouda.jeffrey.profile.heapdump.model.HeapSummary;
import pbouda.jeffrey.shared.common.BytesUtils;

import java.util.List;
import java.util.function.Supplier;

/**
 * Extracts relevant context from a heap dump for use in AI prompts.
 * Uses existing HeapDumpManager methods to get class histogram and summary data.
 */
public class HeapDumpContextExtractor {

    private static final int TOP_CLASSES_LIMIT = 50;

    /**
     * Extract heap dump context using the provided data suppliers.
     *
     * @param histogramByCount supplier for histogram sorted by count
     * @param histogramBySize  supplier for histogram sorted by size
     * @param summarySupplier  supplier for heap summary
     * @return the extracted context
     */
    public HeapDumpContext extract(
            Supplier<List<ClassHistogramEntry>> histogramByCount,
            Supplier<List<ClassHistogramEntry>> histogramBySize,
            Supplier<HeapSummary> summarySupplier) {

        List<ClassHistogramEntry> byCount = histogramByCount.get();
        List<ClassHistogramEntry> bySize = histogramBySize.get();
        HeapSummary summary = summarySupplier.get();

        return new HeapDumpContext(
                byCount.stream().limit(TOP_CLASSES_LIMIT).toList(),
                bySize.stream().limit(TOP_CLASSES_LIMIT).toList(),
                summary.totalInstances(),
                summary.totalBytes()
        );
    }

    /**
     * Format heap dump context as text for inclusion in AI prompt.
     *
     * @param context the heap dump context
     * @return formatted text suitable for AI prompt
     */
    public String formatForPrompt(HeapDumpContext context) {
        StringBuilder sb = new StringBuilder();

        sb.append("## Heap Dump Summary\n");
        sb.append("Total objects: ").append(context.totalObjects()).append("\n");
        sb.append("Total size: ").append(BytesUtils.format(context.totalSize())).append("\n\n");

        sb.append("## Top Classes by Instance Count\n");
        for (ClassHistogramEntry cls : context.topByCount()) {
            sb.append("- ").append(cls.className())
                    .append(" (").append(cls.instanceCount()).append(" instances, ")
                    .append(BytesUtils.format(cls.totalSize())).append(")\n");
        }

        sb.append("\n## Top Classes by Size\n");
        for (ClassHistogramEntry cls : context.topBySize()) {
            sb.append("- ").append(cls.className())
                    .append(" (").append(BytesUtils.format(cls.totalSize())).append(", ")
                    .append(cls.instanceCount()).append(" instances)\n");
        }

        return sb.toString();
    }
}
