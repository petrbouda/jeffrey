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

package cafe.jeffrey.profile.heapdump.sanitizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Sanitizes a corrupted HPROF file <strong>non-destructively</strong> by
 * duplicating it to a sibling {@code <name>.sanitized} file and applying the
 * {@link HprofRepair} operations to the copy. The original file is left
 * untouched.
 *
 * <p>Use this when forensic preservation of the original bytes matters. The
 * trade-off vs. {@link HprofInPlaceSanitizer} is a one-time full file copy
 * (≈ N bytes of disk I/O) before the repair patches are applied.
 *
 * <p>Both sanitizers share the same {@link HprofRepairPlanner} and the
 * underlying byte-level repair operations — only the application target differs.
 */
public final class HprofCopySanitizer {

    private static final Logger LOG = LoggerFactory.getLogger(HprofCopySanitizer.class);

    private HprofCopySanitizer() {
    }

    /**
     * Plans repairs on {@code source} and writes the repaired result to
     * {@code target}. Always produces {@code target} even when the source needed
     * no repairs (a faithful byte-for-byte copy in that case).
     */
    public static SanitizeResult sanitize(Path source, Path target) throws IOException {
        LOG.info("Starting copy HPROF sanitization: source={} target={}", source, target);

        HprofRepairPlan plan;
        try (FileChannel readChannel = FileChannel.open(source, StandardOpenOption.READ)) {
            plan = HprofRepairPlanner.plan(readChannel);
        }

        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        if (!plan.isClean()) {
            try (FileChannel writeChannel = FileChannel.open(target,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                HprofInPlaceSanitizer.apply(writeChannel, plan.repairs());
                writeChannel.force(true);
            }
        }

        LOG.info("Copy HPROF sanitization complete: source={} target={} repairs={} summary={}",
                source, target, plan.repairs().size(), plan.result().summaryMessage());
        return plan.result();
    }
}
